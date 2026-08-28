#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

#include "lspd_native_api.h"

namespace reareye::nativehook {

    void set_log_tag(const char *tag);

    void log_info(const char *fmt, ...);

    void log_warn(const char *fmt, ...);

    void log_error(const char *fmt, ...);

    struct EnvValue {
        std::string value;
        const char *source = "default";
    };

    std::string current_library_path(void *address);

    std::string dirname_of(const std::string &path);

    std::string printable_value(const std::string &value, size_t limit = 4096);

    // 进程身份甄别结果。native_init 时通过 /proc/self/cmdline 与 /proc/self/status 判定。
    enum class ProcessTarget {
        Unknown,
        Weather,
        Gallery,
    };

    ProcessTarget process_target();

    const char *process_target_name(ProcessTarget target);

    /**
     * native_init 统一入口。
     *
     * 保存 LSPosed 提供的 hook_func/unhook_func，随后用 /proc/self/cmdline +
     * /proc/self/status 识别当前进程身份；只有身份匹配（Weather/Gallery）时才返回
     * own_callback 让 LSPosed 继续回调 on_library_loaded，Unknown 进程返回 nullptr，
     * 彻底不参与宿主生命周期。
     */
    NativeOnModuleLoaded init_native(const NativeAPIEntries *entries,
                                     NativeOnModuleLoaded own_callback);

    std::string module_env_path(const char *module_id);

    std::string read_module_env_file(const char *module_id);

    std::string read_module_env_value(const char *module_id, const char *key);

    EnvValue
    read_module_env_value(const char *module_id, const char *key, const char *default_value);

    struct SymbolHook {
        const char *symbol;
        void *replacement;
        void **backup;
    };

    struct InstallResult {
        size_t requested = 0;
        size_t installed = 0;
        size_t missing = 0;
        size_t failed = 0;
    };

    InstallResult
    hook_import_symbols(const char *target_library_suffix, SymbolHook *hooks, size_t hook_count);

    // 内联 hook，底层转调 LSPosed 提供的 hook_func。
    int inline_hook_arm64(void *target, void *replacement, void **backup);

    uintptr_t find_library_base(const char *target_library_suffix);

    void *find_library_symbol_or_offset(void *handle, const char *target_library_suffix,
                                        const char *symbol, uintptr_t offset);

} // namespace reareye::nativehook
