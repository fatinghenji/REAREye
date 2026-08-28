#include "reareye_native_hook.h"

#include <android/log.h>
#include <dlfcn.h>
#include <elf.h>
#include <link.h>
#include <sys/mman.h>
#include <unistd.h>

#include <cerrno>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <string>
#include <utility>

namespace reareye::nativehook {
    namespace {

        constexpr const char *kLogPrefix = "[hk.uwu.reareye] ";

        const char *g_log_tag = "REAREyeNativeHook";

        // LSPosed native_init 注入的函数指针。
        HookFunType g_lsposed_hook_func = nullptr;
        UnhookFunType g_lsposed_unhook_func = nullptr;
        ProcessTarget g_process_target = ProcessTarget::Unknown;

        struct LoadedLibrary {
            uintptr_t base = 0;
            const ElfW(Phdr) *phdr = nullptr;
            size_t phnum = 0;
            std::string name;
        };

        bool ends_with(const char *text, const char *suffix) {
            if (text == nullptr || suffix == nullptr) return false;
            const size_t text_len = std::strlen(text);
            const size_t suffix_len = std::strlen(suffix);
            return text_len >= suffix_len && std::strcmp(text + text_len - suffix_len, suffix) == 0;
        }

        void log_vprint(int priority, const char *fmt, va_list args) {
            std::string format = std::string(kLogPrefix) + (fmt != nullptr ? fmt : "");
            __android_log_vprint(priority, g_log_tag, format.c_str(), args);
        }

        std::string read_proc_file_raw(const char *path, size_t max_bytes = 16384) {
            FILE *file = std::fopen(path, "re");
            if (file == nullptr) return {};
            std::string data(max_bytes, '\0');
            const size_t read = std::fread(data.data(), 1, data.size(), file);
            std::fclose(file);
            data.resize(read);
            return data;
        }

        uintptr_t parse_maps_start_address(const char *line) {
            if (line == nullptr) return 0;
            uintptr_t value = 0;
            for (const char *cursor = line; *cursor != '\0'; ++cursor) {
                const char ch = *cursor;
                if (ch == '-') return value;
                int digit = -1;
                if (ch >= '0' && ch <= '9') digit = ch - '0';
                else if (ch >= 'a' && ch <= 'f') digit = ch - 'a' + 10;
                else if (ch >= 'A' && ch <= 'F') digit = ch - 'A' + 10;
                else return 0;
                value = (value << 4u) | static_cast<uintptr_t>(digit);
            }
            return 0;
        }

        struct LookupData {
            const char *suffix = nullptr;
            LoadedLibrary *library = nullptr;
        };

        int find_library_callback(struct dl_phdr_info *info, size_t, void *data) {
            auto *lookup = static_cast<LookupData *>(data);
            if (info == nullptr || lookup == nullptr || lookup->library == nullptr) return 0;
            if (!ends_with(info->dlpi_name, lookup->suffix)) return 0;
            lookup->library->base = static_cast<uintptr_t>(info->dlpi_addr);
            lookup->library->phdr = info->dlpi_phdr;
            lookup->library->phnum = info->dlpi_phnum;
            lookup->library->name = info->dlpi_name != nullptr ? info->dlpi_name : "";
            return 1;
        }

        bool find_loaded_library(const char *suffix, LoadedLibrary *out) {
            if (suffix == nullptr || out == nullptr) return false;
            LoadedLibrary library{};
            LookupData data{suffix, &library};
            dl_iterate_phdr(find_library_callback, &data);
            if (library.base != 0 && library.phdr != nullptr && library.phnum != 0) {
                *out = library;
                return true;
            }

            FILE *maps = std::fopen("/proc/self/maps", "re");
            if (maps == nullptr) return false;
            char line[1024] = {};
            uintptr_t base = 0;
            std::string name;
            while (std::fgets(line, sizeof(line), maps) != nullptr) {
                if (std::strstr(line, suffix) == nullptr ||
                    std::strstr(line, "r-xp") == nullptr)
                    continue;
                base = parse_maps_start_address(line);
                const char *path = std::strchr(line, '/');
                if (path != nullptr) {
                    name = path;
                    while (!name.empty() && (name.back() == '\n' || name.back() == '\r'))
                        name.pop_back();
                }
                break;
            }
            std::fclose(maps);
            if (base == 0) return false;
            out->base = base;
            out->name = name;
            return true;
        }

        uintptr_t dynamic_ptr(uintptr_t base, ElfW(Addr) value) {
            if (value == 0) return 0;
            // Runtime d_un.d_ptr from bionic is usually already relocated. For file-style values,
            // add library base. Guard with a conservative low-address check.
            if (value < base) return base + static_cast<uintptr_t>(value);
            return static_cast<uintptr_t>(value);
        }

        bool is_supported_relocation(unsigned long type) {
#if defined(__aarch64__)
            return type == R_AARCH64_JUMP_SLOT || type == R_AARCH64_GLOB_DAT ||
                   type == R_AARCH64_ABS64;
#elif defined(__x86_64__)
            return type == R_X86_64_JUMP_SLOT || type == R_X86_64_GLOB_DAT || type == R_X86_64_64;
#else
            (void) type;
            return false;
#endif
        }

        SymbolHook *find_hook(SymbolHook *hooks, size_t hook_count, const char *symbol) {
            if (hooks == nullptr || symbol == nullptr) return nullptr;
            for (size_t i = 0; i < hook_count; ++i) {
                if (hooks[i].symbol != nullptr && std::strcmp(hooks[i].symbol, symbol) == 0) {
                    return &hooks[i];
                }
            }
            return nullptr;
        }

        bool install_slot(const char *library_name, SymbolHook *hook, void **slot) {
            if (hook == nullptr || hook->replacement == nullptr || slot == nullptr) return false;
            void *current = *slot;
            if (current == hook->replacement) {
                log_info("import hook already installed library=%s symbol=%s slot=%p", library_name,
                         hook->symbol, slot);
                return true;
            }
            if (hook->backup != nullptr && *hook->backup == nullptr) {
                *hook->backup = current;
            }
            long page_size = sysconf(_SC_PAGESIZE);
            if (page_size <= 0) page_size = 4096;
            const uintptr_t slot_addr = reinterpret_cast<uintptr_t>(slot);
            const uintptr_t page_start = slot_addr & ~(static_cast<uintptr_t>(page_size) - 1u);
            if (mprotect(reinterpret_cast<void *>(page_start), page_size,
                         PROT_READ | PROT_WRITE) != 0) {
                log_warn("import hook mprotect failed slot=%p errno=%d", slot, errno);
                return false;
            }
            *slot = hook->replacement;
            __builtin___clear_cache(reinterpret_cast<char *>(slot),
                                    reinterpret_cast<char *>(slot) + sizeof(void *));
            log_info(
                    "import hook installed library=%s symbol=%s slot=%p original=%p replacement=%p",
                    library_name != nullptr ? library_name : "<unknown>",
                    hook->symbol,
                    slot,
                    current,
                    hook->replacement
            );
            return true;
        }

        template<typename RelT>
        void hook_relocations(
                const LoadedLibrary &library,
                RelT *rels,
                size_t rel_count,
                const ElfW(Sym) *symtab,
                const char *strtab,
                SymbolHook *hooks,
                size_t hook_count,
                bool *matched,
                InstallResult *result
        ) {
            if (rels == nullptr || symtab == nullptr || strtab == nullptr || hooks == nullptr ||
                result == nullptr)
                return;
            for (size_t i = 0; i < rel_count; ++i) {
                const auto &rel = rels[i];
                const unsigned long type = ELF64_R_TYPE(rel.r_info);
                if (!is_supported_relocation(type)) continue;
                const size_t sym_index = ELF64_R_SYM(rel.r_info);
                const char *symbol = strtab + symtab[sym_index].st_name;
                SymbolHook *hook = find_hook(hooks, hook_count, symbol);
                if (hook == nullptr) continue;
                const auto hook_index = static_cast<size_t>(hook - hooks);
                if (matched != nullptr) matched[hook_index] = true;
                auto **slot = reinterpret_cast<void **>(library.base +
                                                        static_cast<uintptr_t>(rel.r_offset));
                if (install_slot(library.name.c_str(), hook, slot)) result->installed++;
                else result->failed++;
            }
        }

        // ---------------- 进程身份甄别 ----------------

        std::string trim_ascii(std::string value) {
            while (!value.empty() && (value.back() == '\n' || value.back() == '\r' ||
                                      value.back() == '\0' || value.back() == ' ')) {
                value.pop_back();
            }
            return value;
        }

        std::string read_cmdline_package() {
            std::string data = read_proc_file_raw("/proc/self/cmdline", 512);
            // cmdline 以 '\0' 分段，第一段即进程名（对应用进程就是包名）。
            const size_t nul = data.find('\0');
            if (nul != std::string::npos) data.resize(nul);
            return trim_ascii(std::move(data));
        }

        // /proc/self/status 的 Name 字段最长 15 字符（内核 TASK_COMM_LEN 截断），
        // 只能作辅助交叉校验，不能替代 cmdline。
        std::string read_status_field(const char *field) {
            const std::string data = read_proc_file_raw("/proc/self/status", 4096);
            if (data.empty()) return {};
            const std::string prefix = std::string(field) + ":";
            size_t start = 0;
            while (start < data.size()) {
                size_t end = data.find('\n', start);
                if (end == std::string::npos) end = data.size();
                std::string line(data.data() + start, end - start);
                if (line.rfind(prefix, 0) == 0) {
                    std::string value = line.substr(prefix.size());
                    return trim_ascii(std::move(value));
                }
                start = end + 1;
            }
            return {};
        }

        ProcessTarget detect_process_target() {
            const std::string cmdline = read_cmdline_package();
            const std::string comm = read_status_field("Name");
            const std::string uid = read_status_field("Uid");
            log_info(
                    "process identity cmdline=%s comm=%s uid=%s",
                    cmdline.empty() ? "<empty>" : cmdline.c_str(),
                    comm.empty() ? "<empty>" : comm.c_str(),
                    uid.empty() ? "<empty>" : uid.c_str()
            );

            if (cmdline.find("com.miui.weather2") != std::string::npos) {
                return ProcessTarget::Weather;
            }
            if (cmdline.find("com.miui.gallery") != std::string::npos) {
                return ProcessTarget::Gallery;
            }
            // cmdline 异常为空时，用 comm 兜底（受 15 字符截断限制）。
            if (cmdline.empty()) {
                if (comm == "com.miui.weathe" || comm == "com.miui.weather") {
                    return ProcessTarget::Weather;
                }
                if (comm == "com.miui.galler" || comm == "com.miui.gallery") {
                    return ProcessTarget::Gallery;
                }
            }
            return ProcessTarget::Unknown;
        }

    } // namespace

    ProcessTarget process_target() {
        return g_process_target;
    }

    const char *process_target_name(ProcessTarget target) {
        switch (target) {
            case ProcessTarget::Weather:
                return "com.miui.weather2";
            case ProcessTarget::Gallery:
                return "com.miui.gallery";
            default:
                return "<unknown>";
        }
    }

    NativeOnModuleLoaded init_native(const NativeAPIEntries *entries,
                                     NativeOnModuleLoaded own_callback) {
        if (entries == nullptr) {
            log_error("native_init entries=<null>");
            return nullptr;
        }
        // 协议要求不得修改 entries 内容，只读取函数指针。
        g_lsposed_hook_func = entries->hook_func;
        g_lsposed_unhook_func = entries->unhook_func;
        log_info("native_init version=%u hook_func=%p unhook_func=%p",
                 entries->version,
                 reinterpret_cast<void *>(entries->hook_func),
                 reinterpret_cast<void *>(entries->unhook_func));

        g_process_target = detect_process_target();
        if (g_process_target == ProcessTarget::Unknown) {
            log_warn("process identity not matched, skip native hook callback");
            return nullptr;
        }
        log_info("process identity matched target=%s", process_target_name(g_process_target));
        return own_callback;
    }

    std::string current_library_path(void *address) {
        Dl_info info{};
        if (address != nullptr && dladdr(address, &info) != 0 && info.dli_fname != nullptr) {
            return info.dli_fname;
        }
        return {};
    }

    std::string dirname_of(const std::string &path) {
        const size_t slash = path.find_last_of('/');
        if (slash == std::string::npos) return {};
        if (slash == 0) return "/";
        return path.substr(0, slash);
    }

    std::string printable_value(const std::string &value, size_t limit) {
        std::string out;
        out.reserve(value.size());
        for (unsigned char ch: value) {
            if (out.size() >= limit) {
                out += "...";
                break;
            }
            if (ch == '\0') out += "\\0";
            else if (ch == '\n') out += "\\n";
            else if (ch == '\r') out += "\\r";
            else if (ch == '\t') out += "\\t";
            else if (ch >= 0x20 && ch <= 0x7e) out.push_back(static_cast<char>(ch));
            else out.push_back('?');
        }
        return out;
    }

    namespace {

        // 在 /proc/self/maps 中查找以 library_suffix 结尾的映射行，返回其所在目录。
        // wrapper 机制移除后，hook so 位于模块 APK 内，env 文件路径不能再从自身 so
        // 推导，只能锚定目标 app 库（libweather_app.so / libapp_gallery.so）的目录。
        std::string find_library_dir_from_maps(const char *library_suffix) {
            if (library_suffix == nullptr || library_suffix[0] == '\0') return {};
            FILE *maps = std::fopen("/proc/self/maps", "re");
            if (maps == nullptr) return {};
            char line[1024] = {};
            std::string dir;
            while (std::fgets(line, sizeof(line), maps) != nullptr) {
                if (std::strstr(line, library_suffix) == nullptr) continue;
                const char *path = std::strchr(line, '/');
                if (path == nullptr) continue;
                std::string full(path);
                while (!full.empty() && (full.back() == '\n' || full.back() == '\r'))
                    full.pop_back();
                dir = dirname_of(full);
                break;
            }
            std::fclose(maps);
            return dir;
        }

    } // namespace

    std::string module_env_path(const char *module_id) {
        if (module_id == nullptr || module_id[0] == '\0') return {};

        // 锚定目标库：weather → libweather_app.so，gallery → libapp_gallery.so。
        const char *anchor_library = "libweather_app.so";
        if (std::strcmp(module_id, "gallery") == 0) {
            anchor_library = "libapp_gallery.so";
        }

        std::string base_dir = find_library_dir_from_maps(anchor_library);
        if (!base_dir.empty()) {
            log_info("module env dir source=maps library=%s dir=%s", anchor_library,
                     base_dir.c_str());
            return base_dir + "/reareye_" + module_id + ".env";
        }

        // 兜底：锚定库尚未加载时退回自身 so 所在目录（历史行为，防御性保留）。
        std::string self_path = current_library_path(reinterpret_cast<void *>(module_env_path));
        base_dir = dirname_of(self_path);
        log_info("module env dir source=self dir=%s",
                 base_dir.empty() ? "<empty>" : base_dir.c_str());
        if (base_dir.empty()) return {};
        return base_dir + "/reareye_" + module_id + ".env";
    }

    std::string read_module_env_file(const char *module_id) {
        const std::string path = module_env_path(module_id);
        if (path.empty()) return {};
        return read_proc_file_raw(path.c_str(), 16384);
    }

    std::string read_module_env_value(const char *module_id, const char *key) {
        if (module_id == nullptr || module_id[0] == '\0' || key == nullptr || key[0] == '\0')
            return {};
        const std::string data = read_module_env_file(module_id);
        if (data.empty()) return {};

        const std::string prefix = std::string(key) + "=";
        size_t start = 0;
        while (start < data.size()) {
            size_t end = data.find('\n', start);
            if (end == std::string::npos) end = data.size();
            std::string item(data.data() + start, end - start);
            while (!item.empty() && item.back() == '\r') item.pop_back();
            if (item.rfind(prefix, 0) == 0) return item.substr(prefix.size());
            start = end + 1;
        }
        return {};
    }

    EnvValue
    read_module_env_value(const char *module_id, const char *key, const char *default_value) {
        std::string module_value = read_module_env_value(module_id, key);
        EnvValue result{default_value != nullptr ? default_value : "", "default"};
        if (!module_value.empty()) result = {module_value, "module-env"};

        log_info(
                "runtime value module=%s key=%s selected=%s source=%s module_value=%s default=%s env_path=%s",
                module_id != nullptr ? module_id : "<null>",
                key != nullptr ? key : "<null>",
                result.value.empty() ? "<empty>" : printable_value(result.value).c_str(),
                result.source,
                module_value.empty() ? "<empty>" : printable_value(module_value).c_str(),
                default_value != nullptr ? default_value : "<null>",
                module_env_path(module_id).c_str()
        );
        return result;
    }

    void set_log_tag(const char *tag) {
        if (tag != nullptr && tag[0] != '\0') g_log_tag = tag;
    }

    void log_info(const char *fmt, ...) {
        va_list args;
        va_start(args, fmt);
        log_vprint(ANDROID_LOG_INFO, fmt, args);
        va_end(args);
    }

    void log_warn(const char *fmt, ...) {
        va_list args;
        va_start(args, fmt);
        log_vprint(ANDROID_LOG_WARN, fmt, args);
        va_end(args);
    }

    void log_error(const char *fmt, ...) {
        va_list args;
        va_start(args, fmt);
        log_vprint(ANDROID_LOG_ERROR, fmt, args);
        va_end(args);
    }

    uintptr_t find_library_base(const char *target_library_suffix) {
        LoadedLibrary library{};
        if (!find_loaded_library(target_library_suffix, &library)) return 0;
        return library.base;
    }

    void *find_library_symbol_or_offset(void *handle, const char *target_library_suffix,
                                        const char *symbol, uintptr_t offset) {
        if (handle != nullptr && symbol != nullptr && symbol[0] != '\0') {
            dlerror();
            void *address = dlsym(handle, symbol);
            if (address != nullptr) {
                log_info("resolved symbol handle=%p symbol=%s address=%p", handle, symbol, address);
                return address;
            }
            const char *error = dlerror();
            log_warn("resolve symbol failed handle=%p symbol=%s error=%s", handle, symbol,
                     error != nullptr ? error : "<none>");
        }

        if (target_library_suffix == nullptr || offset == 0) return nullptr;
        const uintptr_t base = find_library_base(target_library_suffix);
        if (base == 0) {
            log_warn("resolve offset failed: library not found suffix=%s offset=0x%zx",
                     target_library_suffix, static_cast<size_t>(offset));
            return nullptr;
        }
        void *address = reinterpret_cast<void *>(base + offset);
        log_info("resolved offset suffix=%s base=%p offset=0x%zx address=%p", target_library_suffix,
                 reinterpret_cast<void *>(base), static_cast<size_t>(offset), address);
        return address;
    }

    InstallResult
    hook_import_symbols(const char *target_library_suffix, SymbolHook *hooks, size_t hook_count) {
        InstallResult result{};
        result.requested = hook_count;
        if (target_library_suffix == nullptr || hooks == nullptr || hook_count == 0) return result;

        LoadedLibrary library{};
        if (!find_loaded_library(target_library_suffix, &library) || library.phdr == nullptr ||
            library.phnum == 0) {
            log_warn("install import hooks failed: library not found suffix=%s",
                     target_library_suffix);
            result.missing = hook_count;
            return result;
        }

        const ElfW(Dyn) *dynamic = nullptr;
        size_t dynamic_count = 0;
        for (size_t i = 0; i < library.phnum; ++i) {
            const ElfW(Phdr) &phdr = library.phdr[i];
            if (phdr.p_type == PT_DYNAMIC) {
                dynamic = reinterpret_cast<const ElfW(Dyn) *>(library.base + phdr.p_vaddr);
                dynamic_count = phdr.p_memsz / sizeof(ElfW(Dyn));
                break;
            }
        }
        if (dynamic == nullptr) {
            log_warn("install import hooks failed: dynamic segment missing library=%s",
                     library.name.c_str());
            result.missing = hook_count;
            return result;
        }

        const ElfW(Sym) *symtab = nullptr;
        const char *strtab = nullptr;
        ElfW(Rela) *rela = nullptr;
        size_t rela_count = 0;
        ElfW(Rela) *jmprel = nullptr;
        size_t jmprel_count = 0;
        ElfW(Rel) *rel = nullptr;
        size_t rel_count = 0;

        for (size_t i = 0; i < dynamic_count && dynamic[i].d_tag != DT_NULL; ++i) {
            const ElfW(Dyn) &entry = dynamic[i];
            switch (entry.d_tag) {
                case DT_SYMTAB:
                    symtab = reinterpret_cast<const ElfW(Sym) *>(dynamic_ptr(library.base,
                                                                             entry.d_un.d_ptr));
                    break;
                case DT_STRTAB:
                    strtab = reinterpret_cast<const char *>(dynamic_ptr(library.base,
                                                                        entry.d_un.d_ptr));
                    break;
                case DT_RELA:
                    rela = reinterpret_cast<ElfW(Rela) *>(dynamic_ptr(library.base,
                                                                      entry.d_un.d_ptr));
                    break;
                case DT_RELASZ:
                    rela_count = entry.d_un.d_val / sizeof(ElfW(Rela));
                    break;
                case DT_JMPREL:
                    jmprel = reinterpret_cast<ElfW(Rela) *>(dynamic_ptr(library.base,
                                                                        entry.d_un.d_ptr));
                    break;
                case DT_PLTRELSZ:
                    jmprel_count = entry.d_un.d_val / sizeof(ElfW(Rela));
                    break;
                case DT_REL:
                    rel = reinterpret_cast<ElfW(Rel) *>(dynamic_ptr(library.base,
                                                                    entry.d_un.d_ptr));
                    break;
                case DT_RELSZ:
                    rel_count = entry.d_un.d_val / sizeof(ElfW(Rel));
                    break;
                default:
                    break;
            }
        }

        log_info(
                "install import hooks library=%s base=%p requested=%zu rela=%zu jmprel=%zu rel=%zu",
                library.name.c_str(),
                reinterpret_cast<void *>(library.base),
                hook_count,
                rela_count,
                jmprel_count,
                rel_count
        );

        if (symtab == nullptr || strtab == nullptr) {
            log_warn("install import hooks failed: symtab/strtab missing library=%s",
                     library.name.c_str());
            result.missing = hook_count;
            return result;
        }

        bool matched_stack[64] = {};
        bool *matched = matched_stack;
        if (hook_count > 64) {
            log_warn(
                    "install import hooks hook_count too large=%zu, missing stats may be truncated",
                    hook_count);
            matched = nullptr;
        }

        hook_relocations(library, jmprel, jmprel_count, symtab, strtab, hooks, hook_count, matched,
                         &result);
        hook_relocations(library, rela, rela_count, symtab, strtab, hooks, hook_count, matched,
                         &result);
        hook_relocations(library, rel, rel_count, symtab, strtab, hooks, hook_count, matched,
                         &result);

        if (matched != nullptr) {
            for (size_t i = 0; i < hook_count; ++i) {
                if (!matched[i]) {
                    result.missing++;
                    log_warn("import hook missing library=%s symbol=%s", library.name.c_str(),
                             hooks[i].symbol);
                }
            }
        } else if (result.installed + result.failed < hook_count) {
            result.missing = hook_count - result.installed - result.failed;
        }

        log_info(
                "install import hooks result library=%s requested=%zu installed=%zu missing=%zu failed=%zu",
                library.name.c_str(),
                result.requested,
                result.installed,
                result.missing,
                result.failed
        );
        return result;
    }

    int inline_hook_arm64(void *target, void *replacement, void **backup) {
        // 底层转调 LSPosed native_init 注入的 hook_func（签名一致：target/replace/backup）。
        if (g_lsposed_hook_func == nullptr) {
            log_warn("inline hook skipped: lsposed hook_func unavailable target=%p", target);
            return -100;
        }
        const int status = g_lsposed_hook_func(target, replacement, backup);
        log_info("inline hook target=%p replacement=%p backup=%p status=%d",
                 target, replacement, backup != nullptr ? *backup : nullptr, status);
        return status;
    }

} // namespace reareye::nativehook
