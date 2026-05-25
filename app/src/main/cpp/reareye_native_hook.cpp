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
#include <limits>
#include <string>
#include <utility>

namespace reareye::nativehook {
    namespace {

        constexpr const char *kLogPrefix = "[hk.uwu.reareye] ";
#if defined(__aarch64__)
        constexpr size_t kInlineHookPatchBytes = 20;
#endif

        const char *g_log_tag = "REAREyeNativeHook";

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

        bool make_writable(void *address, size_t length, int prot) {
            if (address == nullptr || length == 0) return false;
            long page_size = sysconf(_SC_PAGESIZE);
            if (page_size <= 0) page_size = 4096;
            uintptr_t start = reinterpret_cast<uintptr_t>(address) &
                              ~(static_cast<uintptr_t>(page_size) - 1u);
            uintptr_t end = (reinterpret_cast<uintptr_t>(address) + length + page_size - 1u) &
                            ~(static_cast<uintptr_t>(page_size) - 1u);
            if (mprotect(reinterpret_cast<void *>(start), end - start, prot) != 0) {
                log_warn("mprotect failed address=%p length=%zu errno=%d", address, length, errno);
                return false;
            }
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
            if (!make_writable(slot, sizeof(void *), PROT_READ | PROT_WRITE)) return false;
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

#if defined(__aarch64__)

        void emit_mov_abs(uint32_t *code, size_t &index, uintptr_t address) {
            auto value = static_cast<uint64_t>(address);
            code[index++] = 0xd2800000u | (static_cast<uint32_t>(value & 0xffffu) << 5u) | 17u;
            code[index++] = 0xf2a00000u | (static_cast<uint32_t>((value >> 16u) & 0xffffu) << 5u) |
                            (1u << 21u) | 17u;
            code[index++] = 0xf2c00000u | (static_cast<uint32_t>((value >> 32u) & 0xffffu) << 5u) |
                            (2u << 21u) | 17u;
            code[index++] = 0xf2e00000u | (static_cast<uint32_t>((value >> 48u) & 0xffffu) << 5u) |
                            (3u << 21u) | 17u;
        }

        void emit_abs_branch(uint32_t *code, size_t &index, uintptr_t address, bool link) {
            emit_mov_abs(code, index, address);
            code[index++] = link ? 0xd63f0220u : 0xd61f0220u;
        }

        int64_t sign_extend(uint64_t value, unsigned bits) {
            const uint64_t sign_bit = 1ull << (bits - 1u);
            value &= (1ull << bits) - 1ull;
            return static_cast<int64_t>((value ^ sign_bit) - sign_bit);
        }

        bool is_bl(uint32_t instruction) {
            return (instruction & 0xfc000000u) == 0x94000000u;
        }

        bool is_b(uint32_t instruction) {
            return (instruction & 0xfc000000u) == 0x14000000u;
        }

        bool is_b_cond(uint32_t instruction) {
            return (instruction & 0xff000010u) == 0x54000000u;
        }

        bool is_cbz_cbnz(uint32_t instruction) {
            return (instruction & 0x7e000000u) == 0x34000000u;
        }

        bool is_tbz_tbnz(uint32_t instruction) {
            return (instruction & 0x7e000000u) == 0x36000000u;
        }

        bool is_adr(uint32_t instruction) {
            return (instruction & 0x9f000000u) == 0x10000000u;
        }

        bool is_adrp(uint32_t instruction) {
            return (instruction & 0x9f000000u) == 0x90000000u;
        }

        bool is_ldr_literal(uint32_t instruction) {
            return (instruction & 0x3b000000u) == 0x18000000u;
        }

        bool is_prfm_literal(uint32_t instruction) {
            return (instruction & 0xff000000u) == 0xd8000000u;
        }

        bool is_ldrsw_literal(uint32_t instruction) {
            return (instruction & 0xff000000u) == 0x98000000u;
        }

        uintptr_t decode_branch_target(uintptr_t pc, uint32_t instruction) {
            const int64_t imm26 = sign_extend(instruction & 0x03ffffffu, 26);
            return pc + (imm26 << 2);
        }

        uintptr_t decode_cond_branch_target(uintptr_t pc, uint32_t instruction) {
            const int64_t imm19 = sign_extend((instruction >> 5u) & 0x7ffffu, 19);
            return pc + (imm19 << 2);
        }

        uintptr_t decode_tbz_target(uintptr_t pc, uint32_t instruction) {
            const int64_t imm14 = sign_extend((instruction >> 5u) & 0x3fffu, 14);
            return pc + (imm14 << 2);
        }

        uintptr_t decode_adr_target(uintptr_t pc, uint32_t instruction) {
            const uint64_t immlo = (instruction >> 29u) & 0x3u;
            const uint64_t immhi = (instruction >> 5u) & 0x7ffffu;
            const int64_t imm = sign_extend((immhi << 2u) | immlo, 21);
            return pc + imm;
        }

        uintptr_t decode_adrp_target(uintptr_t pc, uint32_t instruction) {
            const uint64_t immlo = (instruction >> 29u) & 0x3u;
            const uint64_t immhi = (instruction >> 5u) & 0x7ffffu;
            const int64_t imm = sign_extend((immhi << 2u) | immlo, 21) << 12u;
            return (pc & ~0xfffull) + imm;
        }

        uintptr_t decode_ldr_literal_target(uintptr_t pc, uint32_t instruction) {
            const int64_t imm19 = sign_extend((instruction >> 5u) & 0x7ffffu, 19);
            return pc + (imm19 << 2);
        }

        bool can_encode_imm(int64_t value, unsigned bits, unsigned shift) {
            if ((value & ((1ll << shift) - 1ll)) != 0) return false;
            const int64_t scaled = value >> shift;
            const int64_t min_value = -(1ll << (bits - 1u));
            const int64_t max_value = (1ll << (bits - 1u)) - 1ll;
            return scaled >= min_value && scaled <= max_value;
        }

        uint32_t encode_b_cond(uint32_t instruction, uintptr_t from_pc, uintptr_t to_pc) {
            const auto delta = static_cast<int64_t>(to_pc - from_pc);
            return (instruction & 0xff00001fu) |
                   (static_cast<uint32_t>((delta >> 2u) & 0x7ffffu) << 5u);
        }

        uint32_t encode_cbz_cbnz(uint32_t instruction, uintptr_t from_pc, uintptr_t to_pc) {
            const auto delta = static_cast<int64_t>(to_pc - from_pc);
            return (instruction & 0xff00001fu) |
                   (static_cast<uint32_t>((delta >> 2u) & 0x7ffffu) << 5u);
        }

        uint32_t encode_tbz_tbnz(uint32_t instruction, uintptr_t from_pc, uintptr_t to_pc) {
            const auto delta = static_cast<int64_t>(to_pc - from_pc);
            return (instruction & 0xfff8001fu) |
                   (static_cast<uint32_t>((delta >> 2u) & 0x3fffu) << 5u);
        }

        uint32_t encode_adr(uint32_t instruction, uintptr_t from_pc, uintptr_t to_pc) {
            const auto delta = static_cast<int64_t>(to_pc - from_pc);
            const auto imm = static_cast<uint32_t>(delta & 0x1fffffu);
            return (instruction & 0x9f00001fu) | ((imm & 0x3u) << 29u) |
                   (((imm >> 2u) & 0x7ffffu) << 5u);
        }

        uint32_t encode_adrp(uint32_t instruction, uintptr_t from_pc, uintptr_t to_pc) {
            const int64_t delta_pages =
                    static_cast<int64_t>((to_pc & ~0xfffull) - (from_pc & ~0xfffull)) >> 12u;
            const auto imm = static_cast<uint32_t>(delta_pages & 0x1fffffu);
            return (instruction & 0x9f00001fu) | ((imm & 0x3u) << 29u) |
                   (((imm >> 2u) & 0x7ffffu) << 5u);
        }

        uint32_t encode_ldr_literal(uint32_t instruction, uintptr_t from_pc, uintptr_t to_pc) {
            const auto delta = static_cast<int64_t>(to_pc - from_pc);
            return (instruction & 0xff00001fu) |
                   (static_cast<uint32_t>((delta >> 2u) & 0x7ffffu) << 5u);
        }

        uint32_t
        relocate_instruction(uint32_t *code, size_t &index, uint32_t instruction, uintptr_t from_pc,
                             uintptr_t to_pc) {
            if (is_bl(instruction)) {
                emit_abs_branch(code, index, decode_branch_target(from_pc, instruction), true);
                return 0;
            }
            if (is_b(instruction)) {
                emit_abs_branch(code, index, decode_branch_target(from_pc, instruction), false);
                return 0;
            }
            if (is_b_cond(instruction)) {
                const uintptr_t target = decode_cond_branch_target(from_pc, instruction);
                if (can_encode_imm(static_cast<int64_t>(target - to_pc), 19, 2))
                    return encode_b_cond(instruction, to_pc, target);
                log_warn("inline relocate conditional branch out of range pc=%p target=%p",
                         reinterpret_cast<void *>(from_pc), reinterpret_cast<void *>(target));
                return instruction;
            }
            if (is_cbz_cbnz(instruction)) {
                const uintptr_t target = decode_cond_branch_target(from_pc, instruction);
                if (can_encode_imm(static_cast<int64_t>(target - to_pc), 19, 2))
                    return encode_cbz_cbnz(instruction, to_pc, target);
                log_warn("inline relocate cbz/cbnz out of range pc=%p target=%p",
                         reinterpret_cast<void *>(from_pc), reinterpret_cast<void *>(target));
                return instruction;
            }
            if (is_tbz_tbnz(instruction)) {
                const uintptr_t target = decode_tbz_target(from_pc, instruction);
                if (can_encode_imm(static_cast<int64_t>(target - to_pc), 14, 2))
                    return encode_tbz_tbnz(instruction, to_pc, target);
                log_warn("inline relocate tbz/tbnz out of range pc=%p target=%p",
                         reinterpret_cast<void *>(from_pc), reinterpret_cast<void *>(target));
                return instruction;
            }
            if (is_adr(instruction)) {
                const uintptr_t target = decode_adr_target(from_pc, instruction);
                if (can_encode_imm(static_cast<int64_t>(target - to_pc), 21, 0))
                    return encode_adr(instruction, to_pc, target);
                const uint32_t rd = instruction & 0x1fu;
                emit_mov_abs(code, index, target);
                code[index - 4] = (code[index - 4] & ~0x1fu) | rd;
                code[index - 3] = (code[index - 3] & ~0x1fu) | rd;
                code[index - 2] = (code[index - 2] & ~0x1fu) | rd;
                code[index - 1] = (code[index - 1] & ~0x1fu) | rd;
                return 0;
            }
            if (is_adrp(instruction)) {
                const uintptr_t target = decode_adrp_target(from_pc, instruction);
                if (can_encode_imm(static_cast<int64_t>((target & ~0xfffull) - (to_pc & ~0xfffull)),
                                   21, 12))
                    return encode_adrp(instruction, to_pc, target);
                log_warn("inline relocate adrp out of range pc=%p target=%p",
                         reinterpret_cast<void *>(from_pc), reinterpret_cast<void *>(target));
                return instruction;
            }
            if (is_ldr_literal(instruction) || is_prfm_literal(instruction) ||
                is_ldrsw_literal(instruction)) {
                const uintptr_t target = decode_ldr_literal_target(from_pc, instruction);
                if (can_encode_imm(static_cast<int64_t>(target - to_pc), 19, 2))
                    return encode_ldr_literal(instruction, to_pc, target);
                log_warn("inline relocate literal load out of range pc=%p target=%p",
                         reinterpret_cast<void *>(from_pc), reinterpret_cast<void *>(target));
                return instruction;
            }
            return instruction;
        }

#endif

    } // namespace

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

    std::string module_env_path(const char *module_id) {
        if (module_id == nullptr || module_id[0] == '\0') return {};
        std::string self_path = current_library_path(reinterpret_cast<void *>(module_env_path));
        std::string base_dir = dirname_of(self_path);
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
#if defined(__aarch64__)
        if (target == nullptr || replacement == nullptr || backup == nullptr) return -1;
        auto *trampoline = static_cast<uint32_t *>(mmap(nullptr, 4096,
                                                        PROT_READ | PROT_WRITE | PROT_EXEC,
                                                        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
        if (trampoline == MAP_FAILED) {
            log_warn("inline hook mmap failed errno=%d", errno);
            return -2;
        }
        auto *source = reinterpret_cast<uint32_t *>(target);
        size_t out = 0;
        for (size_t offset = 0; offset < kInlineHookPatchBytes; offset += sizeof(uint32_t)) {
            const uint32_t instruction = source[offset / sizeof(uint32_t)];
            const uintptr_t from_pc = reinterpret_cast<uintptr_t>(target) + offset;
            const auto to_pc = reinterpret_cast<uintptr_t>(trampoline + out);
            const uint32_t relocated = relocate_instruction(trampoline, out, instruction, from_pc,
                                                            to_pc);
            if (relocated != 0) trampoline[out++] = relocated;
        }
        emit_abs_branch(trampoline, out,
                        reinterpret_cast<uintptr_t>(target) + kInlineHookPatchBytes, false);
        __builtin___clear_cache(reinterpret_cast<char *>(trampoline),
                                reinterpret_cast<char *>(trampoline + out));

        if (!make_writable(target, kInlineHookPatchBytes, PROT_READ | PROT_WRITE | PROT_EXEC)) {
            munmap(trampoline, 4096);
            return -3;
        }
        uint32_t patch[5] = {};
        size_t patch_index = 0;
        emit_abs_branch(patch, patch_index, reinterpret_cast<uintptr_t>(replacement), false);
        std::memcpy(target, patch, sizeof(patch));
        __builtin___clear_cache(reinterpret_cast<char *>(target),
                                reinterpret_cast<char *>(target) + sizeof(patch));
        *backup = trampoline;
        log_info("inline hook installed target=%p replacement=%p backup=%p", target, replacement,
                 *backup);
        return 0;
#else
        (void) target;
        (void) replacement;
        (void) backup;
        return -10;
#endif
    }

} // namespace reareye::nativehook
