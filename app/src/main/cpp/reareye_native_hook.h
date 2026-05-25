#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

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

    int inline_hook_arm64(void *target, void *replacement, void **backup);

    uintptr_t find_library_base(const char *target_library_suffix);

    void *find_library_symbol_or_offset(void *handle, const char *target_library_suffix,
                                        const char *symbol, uintptr_t offset);

} // namespace reareye::nativehook
