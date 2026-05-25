#include <dlfcn.h>
#include <unistd.h>

#include <atomic>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>

#include "reareye_native_hook.h"

namespace {

    constexpr const char *kLogTag = "REAREyeWeatherNative";
    constexpr const char *kEnvDeviceLevel = "REAREYE_WEATHER_DEVICE_LEVEL";
    constexpr const char *kEnvUnlockSuperBlur = "REAREYE_WEATHER_UNLOCK_SUPER_BLUR";
    constexpr const char *kEnvOriginalBinary = "REAREYE_WEATHER_ORIGINAL_BINARY";
    constexpr const char *kModuleId = "weather";
    constexpr const char *kOriginalLibraryName = "libweather_app.so";
    constexpr const char *kEntrySymbol = "app_entry_point";
    constexpr const char *kSurfaceBlurProp = "ro.surface_flinger.supports_background_blur";
    constexpr const char *kComputilityVersionProp = "persist.sys.computility.version";
    constexpr const char *kComputilityCpuLevelProp = "persist.sys.computility.cpulevel";
    constexpr const char *kComputilityGpuLevelProp = "persist.sys.computility.gpulevel";
    constexpr int32_t kComputilityFallbackVersion = 2024;
    constexpr int32_t kComputilityMinVersion = 2024;
    constexpr int32_t kComputilityMaxVersion = 2050;

    std::once_flag init_once;
    std::atomic_bool weather_hooks_installed{false};
    std::atomic_uint32_t replacement_log_count{0};
    void *original_library_handle = nullptr;
    void *original_app_entry_point = nullptr;

    using DeviceLevelNewFn = void *(*)(void *runtime, int32_t prefer_cache);
    using DeviceLevelIntFn = int32_t (*)(void *level);
    using DeviceLevelDropFn = void (*)(void *level);
    using SystemPropertiesGetI32Fn = int32_t (*)(const char *key, int32_t key_len,
                                                 int32_t default_value);
    using AppEntryPointFn = void (*)();

    DeviceLevelNewFn original_device_level_get_device_level = nullptr;
    DeviceLevelIntFn original_device_level_get_ram_level = nullptr;
    DeviceLevelIntFn original_device_level_get_cpu_level = nullptr;
    DeviceLevelIntFn original_device_level_get_gpu_level = nullptr;
    DeviceLevelDropFn original_device_level_drop = nullptr;
    SystemPropertiesGetI32Fn original_system_properties_get_i32 = nullptr;

    struct RuntimeValue {
        std::string value;
        const char *source = "default";
    };

    void log_runtime_debug_dump() {
        const std::string env_file = reareye::nativehook::read_module_env_file(kModuleId);
        reareye::nativehook::log_info(
                "runtime debug module-env self=%s path=%s bytes=%zu value=%s",
                reareye::nativehook::current_library_path(
                        reinterpret_cast<void *>(log_runtime_debug_dump)).c_str(),
                reareye::nativehook::module_env_path(kModuleId).c_str(),
                env_file.size(),
                reareye::nativehook::printable_value(env_file).c_str()
        );

        const char *keys[] = {kEnvOriginalBinary, kEnvDeviceLevel, kEnvUnlockSuperBlur};
        for (const char *key: keys) {
            const std::string value = reareye::nativehook::read_module_env_value(kModuleId, key);
            reareye::nativehook::log_info(
                    "runtime debug module-env key=%s value=%s",
                    key,
                    value.empty() ? "<empty>" : reareye::nativehook::printable_value(value).c_str()
            );
        }
    }

    RuntimeValue read_runtime_value(const char *env_key, const char *default_value = "") {
        auto env_value = reareye::nativehook::read_module_env_value(kModuleId, env_key,
                                                                    default_value);
        return RuntimeValue{env_value.value, env_value.source};
    }

    bool value_to_bool(const std::string &value, bool default_value) {
        if (value.empty()) return default_value;
        if (value == "1" || value == "true" || value == "yes" || value == "on" ||
            value == "enabled")
            return true;
        if (value == "0" || value == "false" || value == "no" || value == "off" ||
            value == "disabled")
            return false;
        return default_value;
    }

    int32_t value_to_int(const std::string &value, int32_t default_value) {
        if (value.empty()) return default_value;
        char *end = nullptr;
        errno = 0;
        const long parsed = std::strtol(value.c_str(), &end, 10);
        if (errno != 0 || end == value.c_str()) return default_value;
        return static_cast<int32_t>(parsed);
    }

    void log_replacement_once(const char *symbol, const std::string &detail = {}) {
        uint32_t current = replacement_log_count.fetch_add(1, std::memory_order_relaxed);
        if (current < 96) {
            reareye::nativehook::log_info(
                    "replacement hit symbol=%s %s count=%u",
                    symbol,
                    detail.c_str(),
                    current + 1
            );
        }
    }

    std::string key_detail(const char *key, int32_t key_len) {
        if (key == nullptr) return "key=<null>";
        if (key_len <= 0) return "key=<empty>";
        auto length = static_cast<size_t>(key_len);
        if (length > 96) length = 96;
        std::string out("key=");
        out.reserve(length + 16);
        for (size_t i = 0; i < length; ++i) {
            auto ch = static_cast<unsigned char>(key[i]);
            if (ch >= 0x20 && ch <= 0x7e) out.push_back(static_cast<char>(ch));
            else out.push_back('?');
        }
        if (static_cast<size_t>(key_len) > length) out += "...";
        return out;
    }

    bool key_equals(const char *key, int32_t key_len, const char *target) {
        if (key == nullptr || target == nullptr || key_len <= 0) return false;
        const size_t target_len = std::strlen(target);
        return static_cast<size_t>(key_len) == target_len &&
               std::memcmp(key, target, target_len) == 0;
    }

    int32_t forced_device_level() {
        RuntimeValue value = read_runtime_value(kEnvDeviceLevel, "0");
        const int32_t level = value_to_int(value.value, 0);
        return level >= 1 && level <= 3 ? level : 0;
    }

    int32_t forced_computility_level() {
        switch (forced_device_level()) {
            case 1:
                return 1;
            case 2:
                return 3;
            case 3:
                return 6;
            default:
                return 0;
        }
    }

    bool is_valid_computility_version(int32_t version) {
        return version >= kComputilityMinVersion && version <= kComputilityMaxVersion;
    }

    bool should_unlock_super_blur() {
        RuntimeValue value = read_runtime_value(kEnvUnlockSuperBlur, "0");
        return value_to_bool(value.value, false);
    }

    void *replacement_device_level_get_device_level(void *runtime, int32_t prefer_cache) {
        void *result = original_device_level_get_device_level != nullptr
                       ? original_device_level_get_device_level(runtime, prefer_cache)
                       : nullptr;
        log_replacement_once(
                "DeviceLevel_get_device_level",
                "runtime=" + std::to_string(reinterpret_cast<uintptr_t>(runtime)) +
                " prefer=" + std::to_string(prefer_cache) +
                " result=" + std::to_string(reinterpret_cast<uintptr_t>(result))
        );
        return result;
    }

    int32_t replacement_device_level_get_ram_level(void *level) {
        const int32_t forced = forced_device_level();
        const int32_t original = original_device_level_get_ram_level != nullptr
                                 ? original_device_level_get_ram_level(level) : 0;
        const int32_t result = forced > 0 ? forced : original;
        log_replacement_once(
                "DeviceLevel_get_ram_level",
                "forced=" + std::to_string(forced) + " original=" + std::to_string(original) +
                " result=" + std::to_string(result)
        );
        return result;
    }

    int32_t replacement_device_level_get_cpu_level(void *level) {
        const int32_t forced = forced_device_level();
        const int32_t original = original_device_level_get_cpu_level != nullptr
                                 ? original_device_level_get_cpu_level(level) : 0;
        const int32_t result = forced > 0 ? forced : original;
        log_replacement_once(
                "DeviceLevel_get_cpu_level",
                "forced=" + std::to_string(forced) + " original=" + std::to_string(original) +
                " result=" + std::to_string(result)
        );
        return result;
    }

    int32_t replacement_device_level_get_gpu_level(void *level) {
        const int32_t forced = forced_device_level();
        const int32_t original = original_device_level_get_gpu_level != nullptr
                                 ? original_device_level_get_gpu_level(level) : 0;
        const int32_t result = forced > 0 ? forced : original;
        log_replacement_once(
                "DeviceLevel_get_gpu_level",
                "forced=" + std::to_string(forced) + " original=" + std::to_string(original) +
                " result=" + std::to_string(result)
        );
        return result;
    }

    void replacement_device_level_drop(void *level) {
        log_replacement_once("DeviceLevel_drop");
        if (original_device_level_drop != nullptr) original_device_level_drop(level);
    }

    int32_t
    replacement_system_properties_get_i32(const char *key, int32_t key_len, int32_t default_value) {
        const bool blur_key = key_equals(key, key_len, kSurfaceBlurProp);
        const bool computility_version_key = key_equals(key, key_len, kComputilityVersionProp);
        const bool computility_cpu_key = key_equals(key, key_len, kComputilityCpuLevelProp);
        const bool computility_gpu_key = key_equals(key, key_len, kComputilityGpuLevelProp);
        const bool computility_key =
                computility_version_key || computility_cpu_key || computility_gpu_key;
        const int32_t original = original_system_properties_get_i32 != nullptr
                                 ? original_system_properties_get_i32(key, key_len, default_value)
                                 : default_value;

        if (!blur_key && !computility_key) return original;

        const int32_t device_level = forced_device_level();
        const int32_t computility_level = computility_key ? forced_computility_level() : 0;
        const bool force_blur = blur_key && should_unlock_super_blur();
        const bool force_computility = computility_key && computility_level > 0;
        int32_t result = original;
        if (force_blur) {
            result = 1;
        } else if (force_computility) {
            result = computility_version_key
                     ? (is_valid_computility_version(original) ? original
                                                               : kComputilityFallbackVersion)
                     : computility_level;
        }

        log_replacement_once(
                "SystemProperties_get_i32",
                key_detail(key, key_len) +
                " default=" + std::to_string(default_value) +
                " original=" + std::to_string(original) +
                " device_level=" + std::to_string(device_level) +
                " computility_level=" + std::to_string(computility_level) +
                " force_blur=" + std::to_string(force_blur ? 1 : 0) +
                " force_computility=" + std::to_string(force_computility ? 1 : 0) +
                " result=" + std::to_string(result)
        );
        return result;
    }

    void install_weather_hooks() {
        bool expected = false;
        if (!weather_hooks_installed.compare_exchange_strong(expected, true)) {
            reareye::nativehook::log_info("weather hooks already installed, skip");
            return;
        }

        RuntimeValue original = read_runtime_value(kEnvOriginalBinary, "");
        RuntimeValue level = read_runtime_value(kEnvDeviceLevel, "0");
        RuntimeValue blur = read_runtime_value(kEnvUnlockSuperBlur, "0");
        reareye::nativehook::log_info(
                "install weather hooks config original=%s(%s) level=%s(%s normalized=%d) blur=%s(%s)",
                original.value.empty() ? "<empty>" : original.value.c_str(), original.source,
                level.value.empty() ? "<empty>" : level.value.c_str(), level.source,
                forced_device_level(),
                blur.value.empty() ? "<empty>" : blur.value.c_str(), blur.source
        );

        reareye::nativehook::SymbolHook hooks[] = {
                {"DeviceLevel_get_device_level", reinterpret_cast<void *>(replacement_device_level_get_device_level), reinterpret_cast<void **>(&original_device_level_get_device_level)},
                {"DeviceLevel_get_ram_level",    reinterpret_cast<void *>(replacement_device_level_get_ram_level),    reinterpret_cast<void **>(&original_device_level_get_ram_level)},
                {"DeviceLevel_get_cpu_level",    reinterpret_cast<void *>(replacement_device_level_get_cpu_level),    reinterpret_cast<void **>(&original_device_level_get_cpu_level)},
                {"DeviceLevel_get_gpu_level",    reinterpret_cast<void *>(replacement_device_level_get_gpu_level),    reinterpret_cast<void **>(&original_device_level_get_gpu_level)},
                {"DeviceLevel_drop",             reinterpret_cast<void *>(replacement_device_level_drop),             reinterpret_cast<void **>(&original_device_level_drop)},
                {"SystemProperties_get_i32",     reinterpret_cast<void *>(replacement_system_properties_get_i32),     reinterpret_cast<void **>(&original_system_properties_get_i32)},
        };
        for (const auto &hook: hooks) {
            reareye::nativehook::log_info(
                    "weather hook target symbol=%s replacement=%p backup_slot=%p",
                    hook.symbol,
                    hook.replacement,
                    hook.backup
            );
        }
        auto result = reareye::nativehook::hook_import_symbols(
                kOriginalLibraryName,
                hooks,
                sizeof(hooks) / sizeof(hooks[0])
        );
        reareye::nativehook::log_info(
                "weather hooks install %s requested=%zu installed=%zu missing=%zu failed=%zu",
                result.installed == result.requested ? "complete" : "partial",
                result.requested,
                result.installed,
                result.missing,
                result.failed
        );
    }

    std::string resolve_original_library_path() {
        RuntimeValue value = read_runtime_value(kEnvOriginalBinary, "");
        if (!value.value.empty()) {
            reareye::nativehook::log_info("resolve original source=%s path=%s", value.source,
                                          value.value.c_str());
            return value.value;
        }

        std::string self_path = reareye::nativehook::current_library_path(
                reinterpret_cast<void *>(resolve_original_library_path));
        std::string self_dir = reareye::nativehook::dirname_of(self_path);
        if (!self_dir.empty()) {
            std::string sibling = self_dir + "/" + kOriginalLibraryName;
            reareye::nativehook::log_info("resolve original source=sibling path=%s",
                                          sibling.c_str());
            return sibling;
        }

        reareye::nativehook::log_info("resolve original source=library-name path=%s",
                                      kOriginalLibraryName);
        return kOriginalLibraryName;
    }

    void prepare_original_entry() {
        std::call_once(init_once, [] {
            reareye::nativehook::set_log_tag(kLogTag);
            reareye::nativehook::log_info("prepare original entry start");
            log_runtime_debug_dump();
            const std::string original_path = resolve_original_library_path();
            reareye::nativehook::log_info("dlopen original path=%s", original_path.c_str());
            original_library_handle = dlopen(original_path.c_str(), RTLD_NOW | RTLD_LOCAL);
            if (original_library_handle == nullptr) {
                reareye::nativehook::log_error("dlopen original failed: %s path=%s", dlerror(),
                                               original_path.c_str());
                return;
            }

            install_weather_hooks();

            original_app_entry_point = dlsym(original_library_handle, kEntrySymbol);
            if (original_app_entry_point == nullptr) {
                reareye::nativehook::log_error("dlsym %s failed: %s", kEntrySymbol, dlerror());
                return;
            }

            reareye::nativehook::log_info("weather wrapper ready path=%s", original_path.c_str());
        });
    }

} // namespace

extern "C" [[gnu::visibility("default")]] [[gnu::used]]
void *reareye_weather_prepare_entry() {
    reareye::nativehook::set_log_tag(kLogTag);
    reareye::nativehook::log_info("wrapper prepare entry called");
    prepare_original_entry();
    reareye::nativehook::log_info("wrapper prepare entry return=%p", original_app_entry_point);
    return original_app_entry_point;
}

#if defined(__aarch64__)
extern "C" [[gnu::naked]] [[gnu::visibility("default")]] [[gnu::used]]
void app_entry_point() {
    asm volatile(
            "sub sp, sp, #96\n"
            "stp x29, x30, [sp]\n"
            "mov x29, sp\n"
            "stp x0, x1, [sp, #16]\n"
            "stp x2, x3, [sp, #32]\n"
            "stp x4, x5, [sp, #48]\n"
            "stp x6, x7, [sp, #64]\n"
            "str x8, [sp, #80]\n"
            "bl reareye_weather_prepare_entry\n"
            "mov x9, x0\n"
            "ldr x8, [sp, #80]\n"
            "ldp x6, x7, [sp, #64]\n"
            "ldp x4, x5, [sp, #48]\n"
            "ldp x2, x3, [sp, #32]\n"
            "ldp x0, x1, [sp, #16]\n"
            "ldp x29, x30, [sp]\n"
            "add sp, sp, #96\n"
            "cbz x9, 1f\n"
            "br x9\n"
            "1:\n"
            "ret\n"
            );
}
#else
extern "C" [[gnu::visibility("default")]] [[gnu::used]]
void app_entry_point() {
    prepare_original_entry();
    if (original_app_entry_point != nullptr) {
        reinterpret_cast<AppEntryPointFn>(original_app_entry_point)();
    }
}
#endif
