#include <dlfcn.h>
#include <sys/mman.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <string_view>
#include <vector>
#include <android/log.h>

#include "reareye_native_hook.h"

namespace {

    using reareye::nativehook::ProcessTarget;

    constexpr const char *kLogTag = "REAREyeGalleryNative";
    constexpr const char *kModuleId = "gallery";
    constexpr const char *kOriginalLibraryName = "libapp_gallery.so";
    constexpr const char *kFlutterAppLibraryName = "libapp.so";

    constexpr const char *kEnvBackupServer = "REAREYE_GALLERY_BACKUP_SERVER";
    constexpr const char *kEnvEnableHdrEnhanced = "REAREYE_GALLERY_ENABLE_HDR_ENHANCED";
    constexpr const char *kEnvEnablePdf = "REAREYE_GALLERY_ENABLE_PDF";
    constexpr const char *kEnvEnableOcr = "REAREYE_GALLERY_ENABLE_OCR";
    constexpr const char *kEnvEnableOcrForm = "REAREYE_GALLERY_ENABLE_OCR_FORM";
    constexpr const char *kEnvLongerTrashbinTime = "REAREYE_GALLERY_LONGER_TRASHBIN_TIME";
    constexpr const char *kEnvTrashRetentionDays = "REAREYE_GALLERY_TRASH_RETENTION_DAYS";
    constexpr const char *kEnvEnableIdPhoto = "REAREYE_GALLERY_ENABLE_ID_PHOTO";
    constexpr const char *kEnvEnablePhotoMovie = "REAREYE_GALLERY_ENABLE_PHOTO_MOVIE";
    constexpr const char *kEnvEnableVideoPost = "REAREYE_GALLERY_ENABLE_VIDEO_POST";
    constexpr const char *kEnvEnableVideoEditor = "REAREYE_GALLERY_ENABLE_VIDEO_EDITOR";
    constexpr const char *kEnvEnableMagicMatting = "REAREYE_GALLERY_ENABLE_MAGIC_MATTING";
    constexpr const char *kEnvEnablePrint = "REAREYE_GALLERY_ENABLE_PRINT";
    constexpr const char *kEnvEnablePrivacyWatermark = "REAREYE_GALLERY_ENABLE_PRIVACY_WATERMARK";

    constexpr const char *kMediaEditorAvailableSymbol =
            "_ZN11app_gallery12media_editor18media_editor_utils18media_editor_utils25is_media_editor_available17h204b366d4fb26b5dE";
    constexpr const char *kMediaEditorApiVersionSymbol =
            "_ZN11app_gallery12media_editor18media_editor_utils18media_editor_utils45get_media_editor_api_for_gallery_version_code17h3d723381d339346bE";
    constexpr const char *kBuildUtilIsGlobalSymbol =
            "_ZN11app_gallery5utils10build_util9BuildUtil9is_global17h986194142775939fE";
    constexpr const char *kAiSupportUseAlgoSymbol =
            "_ZN11app_gallery2ai4aicr22ai_core_support_helper19AiCoreSupportHelper27is_support_use_ai_core_algo17h3b939a2a37031fd0E";
    constexpr const char *kAiSupportFullSearchSymbol =
            "_ZN11app_gallery2ai4aicr22ai_core_support_helper19AiCoreSupportHelper30is_support_ai_core_full_search17h8685a5c91b0904b6E";
    constexpr const char *kAiCheckAlgoSupportSymbol =
            "_ZN11app_gallery2ai4aicr22ai_core_support_helper19AiCoreSupportHelper18check_algo_support17hb8f474aa583e01d8E";
    constexpr const char *kSearchClipSupportSymbol =
            "_ZN11app_gallery6search5utils19search_clip_manager17SearchClipManager15is_support_clip17h83aea50cb6275d6aE";
    constexpr const char *kTrashBinStartMsSymbol =
            "_ZN11app_gallery9data_sync5utils11trash_utils9TrashUtil22get_trash_bin_start_ms17hfd7f441e965addebE";
    constexpr const char *kSupportBackupOnlyWifiSymbol =
            "_ZN11app_gallery9data_sync5utils12feature_util27is_support_backup_only_wifi17ha7a37b3c6e30f1cdE";
    constexpr const char *kFeatureParserHasFeatureSymbol = "Feature_parser_has_feature";
    constexpr const char *kFeatureParserGetBooleanSymbol = "Feature_parser_get_boolean";
    constexpr const char *kMiuiOsBuildIsInternationalBuildSymbol = "MiuiOsBuild_is_international_build";
    constexpr const char *kMiuiOsBuildIsGlobalBuildSymbol = "MiuiOsBuild_is_global_build";

    constexpr uintptr_t kMediaEditorAvailableOffset = 0x1adfb94;
    constexpr uintptr_t kMediaEditorApiVersionOffset = 0x1adfd44;
    constexpr uintptr_t kBuildUtilIsGlobalOffset = 0x15271d4;
    constexpr uintptr_t kAiSupportUseAlgoOffset = 0x1449854;
    constexpr uintptr_t kAiSupportFullSearchOffset = 0x14496f8;
    constexpr uintptr_t kAiCheckAlgoSupportOffset = 0x14499b0;
    constexpr uintptr_t kSearchClipSupportOffset = 0x1bb80e4;
    constexpr uintptr_t kTrashBinStartMsOffset = 0x187f36c;
    constexpr uintptr_t kSupportBackupOnlyWifiOffset = 0x1869090;
    constexpr uintptr_t kFlutterTextRangeResetOffset = 0x8cab08;
    constexpr uintptr_t kFlutterPrivacyWatermarkTextSetterOffset = 0x8cc218;
    constexpr uintptr_t kFlutterPrivacyWatermarkTextAppendOffset = 0x8cc608;
    [[maybe_unused]] constexpr uintptr_t kFlutterPrivacyWatermarkFormatterCtorOffset = 0x8c6e54;
    [[maybe_unused]] constexpr uintptr_t kFlutterPrivacyWatermarkControllerLimitCmpOffset = 0x8c5ebc;
    constexpr uint32_t kFlutterPrivacyWatermarkMaxChars = 100;
    // libapp.so 回调到达时 Flutter 运行时可能仍在映射中；只在水印开关开启时短暂等待。
    constexpr int kFlutterMappingRetryAttempts = 20;
    constexpr useconds_t kFlutterMappingRetryIntervalUs = 50000;

    constexpr uint32_t kGroupHdr = 1u << 0u;
    constexpr uint32_t kGroupPdf = 1u << 1u;
    constexpr uint32_t kGroupOcr = 1u << 2u;
    constexpr uint32_t kGroupOcrForm = 1u << 3u;
    constexpr uint32_t kGroupIdPhoto = 1u << 4u;
    constexpr uint32_t kGroupPhotoMovie = 1u << 5u;
    constexpr uint32_t kGroupVideoPost = 1u << 6u;
    constexpr uint32_t kGroupVideoEditor = 1u << 7u;
    constexpr uint32_t kGroupMagicMatting = 1u << 8u;
    constexpr uint32_t kGroupPrint = 1u << 9u;
    constexpr uint32_t kGroupPrivacy = 1u << 10u;

    struct GalleryConfig {
        int32_t backup_server = 0;
        bool enable_hdr_enhanced = false;
        bool enable_pdf = false;
        bool enable_ocr = false;
        bool enable_ocr_form = false;
        bool longer_trashbin_time = false;
        bool enable_id_photo = false;
        bool enable_photo_movie = false;
        bool enable_video_post = false;
        bool enable_video_editor = false;
        bool enable_magic_matting = false;
        bool enable_print = false;
        bool enable_privacy_watermark = false;
        int64_t trash_retention_days = 365;
    };

    struct FeatureKey {
        const char *key;
        uint32_t groups;
    };

    constexpr FeatureKey kFeatureKeys[] = {
            {"gallery_support_media_feature",
                                                        kGroupVideoEditor | kGroupOcr |
                                                        kGroupPhotoMovie | kGroupVideoPost |
                                                        kGroupMagicMatting | kGroupPrint},
            {"gallery_support_analytic_face_and_scene", kGroupOcr | kGroupOcrForm},
            {"gallery_video_editor_entrance",           kGroupVideoEditor | kGroupVideoPost},
            {"gallery_support_vlog_entrance",           kGroupVideoEditor},
            {"gallery_super_low_vlog_entrance",         kGroupVideoEditor},
            {"gallery_support_art_still_entrance",      kGroupPhotoMovie | kGroupVideoEditor},
            {"gallery_support_video_post_entrance",     kGroupVideoPost},
            {"gallery_support_portrait_color",          kGroupMagicMatting},
            {"gallery_support_remover",                 kGroupMagicMatting},
            {"gallery_support_magic_matting_entrance",  kGroupMagicMatting},
            {"gallery_support_video_frame",             kGroupVideoPost | kGroupVideoEditor},
            {"gallery_support_photo_movie",             kGroupPhotoMovie},
            {"gallery_support_print",                   kGroupPrint},
            {"gallery_support_segment",                 kGroupOcr | kGroupOcrForm |
                                                        kGroupMagicMatting},
            {"support_hdr_enhance",                     kGroupHdr},
            {"support_local_ocr",                       kGroupOcr},
            {"support_ocr",                             kGroupOcr},
            {"support_recognize_form",                  kGroupOcrForm},
            {"support_table_recognition",               kGroupOcrForm},
            {"support_pdf",                             kGroupPdf},
            {"support_pic_to_pdf",                      kGroupPdf},
            {"support_print",                           kGroupPrint},
            {"support_id_photo",                        kGroupIdPhoto},
            {"support_photo_movie",                     kGroupPhotoMovie},
            {"support_video_post",                      kGroupVideoPost},
            {"support_vlog",                            kGroupVideoEditor},
            {"support_magic_matting",                   kGroupMagicMatting},
            {"support_privacy_watermark",               kGroupPrivacy},
            {"gallery_support_privacy_watermark",       kGroupPrivacy},
            {"privacy_watermark",                       kGroupPrivacy},
            {"ai_watermark",                            kGroupPrivacy},
    };

    std::atomic_bool gallery_hooks_installed{false};
    std::atomic_bool flutter_app_hooks_installed{false};
    std::atomic_uint32_t replacement_log_count{0};
    void *original_library_handle = nullptr;
    GalleryConfig config{};

    using MediaEditorAvailableFn = uint64_t (*)();
    using MediaEditorApiVersionFn = int64_t (*)();
    using BuildUtilIsGlobalFn = uint64_t (*)(int64_t self);
    using BoolNoArgFn = uint64_t (*)();
    using BoolOneArgFn = uint64_t (*)(int64_t arg);
    using BoolIntArgFn = uint64_t (*)(int32_t arg);
    using TrashBinStartMsFn = uint64_t (*)();
    using FeatureParserHasFeatureFn = uint8_t (*)(const char *key, int32_t key_len,
                                                  int32_t default_value);
    using FeatureParserGetBooleanFn = uint8_t (*)(const char *key, int32_t key_len,
                                                  int32_t default_value);
    using MiuiOsBuildBoolFn = uint8_t (*)();
    using FlutterTextSetterFn = uint64_t (*)(uint64_t arg0, uint64_t arg1, uint64_t arg2);

    MediaEditorAvailableFn original_media_editor_available = nullptr;
    MediaEditorApiVersionFn original_media_editor_api_version = nullptr;
    BuildUtilIsGlobalFn original_build_util_is_global = nullptr;
    BoolNoArgFn original_ai_support_use_algo = nullptr;
    BoolNoArgFn original_ai_support_full_search = nullptr;
    BoolIntArgFn original_ai_check_algo_support = nullptr;
    BoolOneArgFn original_search_clip_support = nullptr;
    TrashBinStartMsFn original_trash_bin_start_ms = nullptr;
    BoolNoArgFn original_support_backup_only_wifi = nullptr;
    FeatureParserHasFeatureFn original_feature_parser_has_feature = nullptr;
    FeatureParserGetBooleanFn original_feature_parser_get_boolean = nullptr;
    MiuiOsBuildBoolFn original_miui_os_build_is_international_build = nullptr;
    MiuiOsBuildBoolFn original_miui_os_build_is_global_build = nullptr;
    FlutterTextSetterFn original_flutter_privacy_watermark_text_setter = nullptr;
    FlutterTextSetterFn original_flutter_privacy_watermark_text_append = nullptr;

    std::string read_runtime_value(const char *env_key, const char *default_value = "") {
        return reareye::nativehook::read_module_env_value(kModuleId, env_key, default_value).value;
    }

    bool is_true_string(const std::string &value) {
        return value == "1" || value == "true" || value == "TRUE" || value == "yes" ||
               value == "on";
    }

    bool runtime_bool(const char *env_key, bool default_value = false) {
        return is_true_string(read_runtime_value(env_key, default_value ? "1" : "0"));
    }

    int32_t runtime_i32(const char *env_key, int32_t default_value) {
        const std::string value = read_runtime_value(env_key,
                                                     std::to_string(default_value).c_str());
        if (value.empty()) return default_value;
        char *end = nullptr;
        errno = 0;
        const long parsed = std::strtol(value.c_str(), &end, 10);
        if (errno != 0 || end == value.c_str()) return default_value;
        return static_cast<int32_t>(parsed);
    }

    int64_t runtime_i64(const char *env_key, int64_t default_value) {
        const std::string value = read_runtime_value(env_key,
                                                     std::to_string(default_value).c_str());
        if (value.empty()) return default_value;
        char *end = nullptr;
        errno = 0;
        const long long parsed = std::strtoll(value.c_str(), &end, 10);
        if (errno != 0 || end == value.c_str()) return default_value;
        return parsed;
    }

    void load_config() {
        config.backup_server = std::clamp<int32_t>(runtime_i32(kEnvBackupServer, 0), 0, 2);
        config.enable_hdr_enhanced = runtime_bool(kEnvEnableHdrEnhanced);
        config.enable_pdf = runtime_bool(kEnvEnablePdf);
        config.enable_ocr = runtime_bool(kEnvEnableOcr);
        config.enable_ocr_form = runtime_bool(kEnvEnableOcrForm);
        config.longer_trashbin_time = runtime_bool(kEnvLongerTrashbinTime);
        config.enable_id_photo = runtime_bool(kEnvEnableIdPhoto);
        config.enable_photo_movie = runtime_bool(kEnvEnablePhotoMovie);
        config.enable_video_post = runtime_bool(kEnvEnableVideoPost);
        config.enable_video_editor = runtime_bool(kEnvEnableVideoEditor);
        config.enable_magic_matting = runtime_bool(kEnvEnableMagicMatting);
        config.enable_print = runtime_bool(kEnvEnablePrint);
        config.enable_privacy_watermark = runtime_bool(kEnvEnablePrivacyWatermark);
        config.trash_retention_days = std::clamp<int64_t>(runtime_i64(kEnvTrashRetentionDays, 365),
                                                          1, 3650);
        reareye::nativehook::log_info(
                "gallery config backup=%d hdr=%d pdf=%d ocr=%d ocr_form=%d trash=%d trash_days=%lld id_photo=%d photo_movie=%d video_post=%d video_editor=%d magic_matting=%d print=%d privacy_watermark=%d",
                config.backup_server,
                config.enable_hdr_enhanced,
                config.enable_pdf,
                config.enable_ocr,
                config.enable_ocr_form,
                config.longer_trashbin_time,
                static_cast<long long>(config.trash_retention_days),
                config.enable_id_photo,
                config.enable_photo_movie,
                config.enable_video_post,
                config.enable_video_editor,
                config.enable_magic_matting,
                config.enable_print,
                config.enable_privacy_watermark
        );
    }

    bool is_group_enabled(uint32_t groups) {
        if (groups == 0) return false;
        if ((groups & kGroupHdr) != 0 && config.enable_hdr_enhanced) return true;
        if ((groups & kGroupPdf) != 0 && config.enable_pdf) return true;
        if ((groups & kGroupOcr) != 0 && config.enable_ocr) return true;
        if ((groups & kGroupOcrForm) != 0 && config.enable_ocr_form) return true;
        if ((groups & kGroupIdPhoto) != 0 && config.enable_id_photo) return true;
        if ((groups & kGroupPhotoMovie) != 0 && config.enable_photo_movie) return true;
        if ((groups & kGroupVideoPost) != 0 && config.enable_video_post) return true;
        if ((groups & kGroupVideoEditor) != 0 && config.enable_video_editor) return true;
        if ((groups & kGroupMagicMatting) != 0 && config.enable_magic_matting) return true;
        if ((groups & kGroupPrint) != 0 && config.enable_print) return true;
        if ((groups & kGroupPrivacy) != 0 && config.enable_privacy_watermark) return true;
        return false;
    }

    void log_replacement_once(const char *symbol, const std::string &detail = {}) {
        uint32_t current = replacement_log_count.fetch_add(1, std::memory_order_relaxed);
        if (current < 160) {
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

    const FeatureKey *matched_feature_key(const char *key, int32_t key_len) {
        for (const auto &feature_key: kFeatureKeys) {
            if (key_equals(key, key_len, feature_key.key)) return &feature_key;
        }
        return nullptr;
    }

    uint64_t now_ms() {
        using namespace std::chrono;
        return static_cast<uint64_t>(duration_cast<milliseconds>(
                system_clock::now().time_since_epoch()).count());
    }

    bool has_creation_feature_enabled() {
        return config.enable_id_photo || config.enable_photo_movie || config.enable_video_post ||
               config.enable_magic_matting;
    }

    bool has_media_editor_feature_enabled() {
        return has_creation_feature_enabled() || config.enable_video_editor;
    }

    bool has_ai_feature_enabled() {
        return config.enable_ocr || config.enable_ocr_form;
    }

    struct ExecutableMapping {
        uintptr_t start = 0;
        uintptr_t end = 0;
        uintptr_t base = 0;
        std::string path;
    };

    struct FlutterWatermarkHookTargets {
        uintptr_t reset = 0;
        uintptr_t setter = 0;
        uintptr_t append = 0;
        uintptr_t formatter_ctor = 0;
        uintptr_t formatter_smi_limit_mov = 0;
        uintptr_t formatter_text_limit_cmp = 0;
        uintptr_t setter_text_length_cmp = 0;
        uintptr_t controller_text_length_cmp = 0;
    };

    bool parse_maps_range(const char *line, uintptr_t *start, uintptr_t *end) {
        if (line == nullptr || start == nullptr || end == nullptr) return false;
        char *cursor = nullptr;
        const auto parsed_start = static_cast<uintptr_t>(std::strtoull(line, &cursor, 16));
        if (cursor == nullptr || *cursor != '-') return false;
        const auto parsed_end = static_cast<uintptr_t>(std::strtoull(cursor + 1, &cursor, 16));
        if (parsed_start == 0 || parsed_end <= parsed_start) return false;
        *start = parsed_start;
        *end = parsed_end;
        return true;
    }

    bool find_executable_mapping(const char *library_suffix, ExecutableMapping *out) {
        if (library_suffix == nullptr || out == nullptr) return false;
        FILE *maps = std::fopen("/proc/self/maps", "re");
        if (maps == nullptr) return false;

        char line[1024] = {};
        bool found = false;
        while (std::fgets(line, sizeof(line), maps) != nullptr) {
            if (std::strstr(line, library_suffix) == nullptr) continue;
            const char *space = std::strchr(line, ' ');
            if (space == nullptr) continue;
            while (*space == ' ') ++space;
            if (!(space[0] == 'r' && space[2] == 'x')) continue;

            uintptr_t start = 0;
            uintptr_t end = 0;
            if (!parse_maps_range(line, &start, &end)) continue;

            const char *path = std::strchr(space, '/');
            ExecutableMapping mapping{};
            mapping.start = start;
            mapping.end = end;
            mapping.base = reareye::nativehook::find_library_base(library_suffix);
            if (path != nullptr) {
                mapping.path = path;
                while (!mapping.path.empty() &&
                       (mapping.path.back() == '\n' || mapping.path.back() == '\r')) {
                    mapping.path.pop_back();
                }
            }
            *out = mapping;
            found = true;
            break;
        }
        std::fclose(maps);
        return found;
    }

    bool contains_address(const ExecutableMapping &mapping, uintptr_t address) {
        return address >= mapping.start && address < mapping.end;
    }

    bool
    contains_range(const ExecutableMapping &mapping, uintptr_t address, size_t size) {
        return contains_address(mapping, address) && size <= mapping.end - address;
    }

    size_t relative_to_base(const ExecutableMapping &mapping, uintptr_t address) {
        const uintptr_t base = mapping.base != 0 ? mapping.base : mapping.start;
        return address >= base ? static_cast<size_t>(address - base) : 0;
    }

#if defined(__aarch64__)

    constexpr uint32_t kArm64AotPrologue0 = 0xa9bf79fdu; // STP X29, X30, [X15,#-0x10]!
    constexpr uint32_t kArm64AotPrologue1 = 0xaa0f03fdu; // MOV X29, X15
    constexpr uint32_t kArm64Ret = 0xd65f03c0u;

    uint32_t instruction_at(uintptr_t address, size_t index = 0) {
        return reinterpret_cast<const uint32_t *>(address)[index];
    }

    int64_t sign_extend(uint64_t value, unsigned bits) {
        const uint64_t sign_bit = 1ull << (bits - 1u);
        value &= (1ull << bits) - 1ull;
        return static_cast<int64_t>((value ^ sign_bit) - sign_bit);
    }

    bool is_sub_x15_x15_imm(uint32_t instruction) {
        return (instruction & 0xffc003ffu) == 0xd10001efu;
    }

    bool looks_like_flutter_aot_function(const ExecutableMapping &mapping, uintptr_t address) {
        if (!contains_range(mapping, address, 12)) return false;
        return instruction_at(address, 0) == kArm64AotPrologue0 &&
               instruction_at(address, 1) == kArm64AotPrologue1 &&
               is_sub_x15_x15_imm(instruction_at(address, 2));
    }

    bool is_bl(uint32_t instruction) {
        return (instruction & 0xfc000000u) == 0x94000000u;
    }

    uintptr_t decode_bl_target(uintptr_t pc, uint32_t instruction) {
        const int64_t imm26 = sign_extend(instruction & 0x03ffffffu, 26);
        return pc + static_cast<uintptr_t>(imm26 << 2);
    }

    bool is_b_cond(uint32_t instruction) {
        return (instruction & 0xff000010u) == 0x54000000u;
    }

    bool is_cmp_x_imm(uint32_t instruction, uint32_t imm) {
        return (instruction & 0xffc0001fu) == 0xf100001fu && ((instruction >> 10u) & 0xfffu) == imm;
    }

    uint32_t encode_cmp_x_imm(uint32_t instruction, uint32_t imm) {
        return (instruction & ~0x003ffc00u) | ((imm & 0xfffu) << 10u);
    }

    bool is_cmp_w_imm(uint32_t instruction, uint32_t imm) {
        return (instruction & 0xffc0001fu) == 0x7100001fu && ((instruction >> 10u) & 0xfffu) == imm;
    }

    bool is_movz_x_imm(uint32_t instruction, uint32_t rd, uint32_t imm) {
        return (instruction & 0xffe00000u) == 0xd2800000u &&
               (instruction & 0x1fu) == rd &&
               ((instruction >> 5u) & 0xffffu) == imm;
    }

    uint32_t encode_movz_x_imm(uint32_t rd, uint32_t imm) {
        return 0xd2800000u | ((imm & 0xffffu) << 5u) | (rd & 0x1fu);
    }

    int32_t decode_signed_imm9(uint32_t instruction) {
        uint32_t value = (instruction >> 12u) & 0x1ffu;
        if ((value & 0x100u) != 0) value |= 0xfffffe00u;
        return static_cast<int32_t>(value);
    }

    bool is_stur_x_to(uint32_t instruction, uint32_t rn, int32_t imm) {
        return (instruction & 0xffc00000u) == 0xf8000000u &&
               ((instruction >> 5u) & 0x1fu) == rn &&
               decode_signed_imm9(instruction) == imm;
    }

    bool is_str_x_unsigned_to(uint32_t instruction, uint32_t rn, int32_t imm) {
        if ((instruction & 0xffc00000u) != 0xf9000000u) return false;
        if (((instruction >> 5u) & 0x1fu) != rn) return false;
        return static_cast<int32_t>(((instruction >> 10u) & 0xfffu) << 3u) == imm;
    }

    bool is_ldur_w_from(uint32_t instruction, uint32_t rn, int32_t imm) {
        return (instruction & 0xffc00000u) == 0xb8400000u &&
               ((instruction >> 5u) & 0x1fu) == rn &&
               decode_signed_imm9(instruction) == imm;
    }

    bool is_ldur_x_from(uint32_t instruction, uint32_t rn, int32_t imm) {
        return (instruction & 0xffc00000u) == 0xf8400000u &&
               ((instruction >> 5u) & 0x1fu) == rn &&
               decode_signed_imm9(instruction) == imm;
    }

    bool is_stur_w_to(uint32_t instruction, uint32_t rn, int32_t imm) {
        return (instruction & 0xffc00000u) == 0xb8000000u &&
               ((instruction >> 5u) & 0x1fu) == rn &&
               decode_signed_imm9(instruction) == imm;
    }

    bool branch_targets(uintptr_t pc, uint32_t instruction, uintptr_t target) {
        return is_bl(instruction) && decode_bl_target(pc, instruction) == target;
    }

    bool matches_reset_helper(const ExecutableMapping &mapping, uintptr_t address) {
        if (!contains_range(mapping, address, 72) ||
            !looks_like_flutter_aot_function(mapping, address))
            return false;
        const uint32_t i3 = instruction_at(address, 3);
        const uint32_t i4 = instruction_at(address, 4);
        const uint32_t i5 = instruction_at(address, 5);
        const uint32_t i6 = instruction_at(address, 6);
        const uint32_t i7 = instruction_at(address, 7);
        const uint32_t i10 = instruction_at(address, 10);
        const uint32_t i11 = instruction_at(address, 11);
        const uint32_t i14 = instruction_at(address, 14);
        const uint32_t i15 = instruction_at(address, 15);
        const uint32_t i16 = instruction_at(address, 16);
        const uint32_t i17 = instruction_at(address, 17);
        return i3 == 0xaa0103e0u &&
               i4 == 0xf81f83a1u &&
               is_bl(i5) &&
               i6 == 0xf85f83a0u &&
               is_stur_w_to(i7, 0, 0x27) &&
               i10 == 0xd2800002u &&
               i11 == 0xd2800203u &&
               i14 == 0xaa1603e0u &&
               i15 == 0xaa1d03efu &&
               i16 == 0xa8c179fdu &&
               i17 == kArm64Ret;
    }

    bool recent_cmp_x_14_before(const ExecutableMapping &mapping, uintptr_t function,
                                size_t branch_index, size_t window) {
        const size_t begin = branch_index > window ? branch_index - window : 0;
        for (size_t index = begin; index < branch_index; ++index) {
            if (!contains_range(mapping, function + index * sizeof(uint32_t),
                                sizeof(uint32_t)))
                return false;
            if (is_cmp_x_imm(instruction_at(function, index), 14)) return true;
        }
        return false;
    }

    bool recent_cmp_w_32_before(const ExecutableMapping &mapping, uintptr_t function,
                                size_t branch_index, size_t window) {
        const size_t begin = branch_index > window ? branch_index - window : 0;
        for (size_t index = begin; index < branch_index; ++index) {
            if (!contains_range(mapping, function + index * sizeof(uint32_t),
                                sizeof(uint32_t)))
                return false;
            if (is_cmp_w_imm(instruction_at(function, index), 32)) return true;
        }
        return false;
    }

    bool
    matches_setter_candidate(const ExecutableMapping &mapping, uintptr_t address, uintptr_t reset) {
        if (!contains_address(mapping, reset) || !contains_range(mapping, address, 0x90) ||
            !looks_like_flutter_aot_function(mapping, address)) {
            return false;
        }
        if (instruction_at(address, 3) != 0xaa0103e0u) return false; // MOV X0, X1

        bool reads_text_length = false;
        for (size_t index = 3; index < 16; ++index) {
            if (is_ldur_w_from(instruction_at(address, index), 0, 0x27)) {
                reads_text_length = true;
                break;
            }
        }
        if (!reads_text_length) return false;

        for (size_t index = 6; index < 36; ++index) {
            const uintptr_t pc = address + index * sizeof(uint32_t);
            const uint32_t instruction = instruction_at(address, index);
            if (!branch_targets(pc, instruction, reset)) continue;
            if (index < 3 || instruction_at(address, index - 1) != 0xaa0003e1u)
                continue; // MOV X1, X0
            if (!is_b_cond(instruction_at(address, index - 2))) continue;
            if (!recent_cmp_x_14_before(mapping, address, index, 8)) continue;
            return true;
        }
        return false;
    }

    bool append_has_early_update_shape(const ExecutableMapping &mapping, uintptr_t address) {
        if (!contains_range(mapping, address, 0x80)) return false;
        bool reads_backing = false;
        bool reads_length = false;
        bool writes_length = false;
        for (size_t index = 3; index < 32; ++index) {
            const uint32_t instruction = instruction_at(address, index);
            if (is_ldur_w_from(instruction, 4, 0x23)) reads_backing = true;
            if (is_ldur_w_from(instruction, 4, 0x27)) reads_length = true;
            if (is_stur_w_to(instruction, 4, 0x27)) writes_length = true;
        }
        return reads_backing && reads_length && writes_length;
    }

    bool
    matches_append_candidate(const ExecutableMapping &mapping, uintptr_t address, uintptr_t reset) {
        if (!contains_address(mapping, reset) || !contains_range(mapping, address, 0x120) ||
            !looks_like_flutter_aot_function(mapping, address)) {
            return false;
        }
        if (instruction_at(address, 3) != 0xaa0103e4u) return false; // MOV X4, X1
        if (!append_has_early_update_shape(mapping, address)) return false;

        for (size_t index = 24; index < 96; ++index) {
            const uintptr_t pc = address + index * sizeof(uint32_t);
            const uint32_t instruction = instruction_at(address, index);
            if (!branch_targets(pc, instruction, reset)) continue;
            if (!recent_cmp_w_32_before(mapping, address, index, 8)) continue;
            return true;
        }
        return false;
    }

    bool is_add_imm_to_reg(uint32_t instruction, uint32_t rd, uint32_t rn) {
        return (instruction & 0xff000000u) == 0x91000000u &&
               (instruction & 0x1fu) == rd &&
               ((instruction >> 5u) & 0x1fu) == rn;
    }

    bool is_ldr_x_unsigned_from(uint32_t instruction, uint32_t rt, uint32_t rn) {
        return (instruction & 0xffc00000u) == 0xf9400000u &&
               (instruction & 0x1fu) == rt &&
               ((instruction >> 5u) & 0x1fu) == rn;
    }

    bool is_mov_reg(uint32_t instruction, uint32_t rd, uint32_t rn) {
        return instruction == (0xaa0003e0u | ((rn & 0x1fu) << 16u) | (rd & 0x1fu));
    }

    bool matches_formatter_limit_sequence(const ExecutableMapping &mapping, uintptr_t limit_mov) {
        if (limit_mov < mapping.start + 9 * sizeof(uint32_t)) return false;
        const uintptr_t ctor = limit_mov - 9 * sizeof(uint32_t);
        if (!contains_range(mapping, ctor, 20 * sizeof(uint32_t))) return false;
        return looks_like_flutter_aot_function(mapping, ctor) &&
               is_movz_x_imm(instruction_at(limit_mov, 0), 0, 0x1C) &&
               is_stur_x_to(instruction_at(limit_mov, 1), 4, 0x2F) &&
               is_movz_x_imm(instruction_at(limit_mov, 2), 16, 0xE) &&
               is_str_x_unsigned_to(instruction_at(limit_mov, 3), 15, 0);
    }

    bool matches_setter_text_limit_cmp(const ExecutableMapping &mapping, uintptr_t cmp) {
        if (cmp < mapping.start + 14 * sizeof(uint32_t)) return false;
        const uintptr_t setter = cmp - 14 * sizeof(uint32_t);
        if (!contains_range(mapping, setter, 18 * sizeof(uint32_t)) ||
            !looks_like_flutter_aot_function(mapping, setter))
            return false;
        return is_mov_reg(instruction_at(setter, 3), 0, 1) &&
               is_ldur_w_from(instruction_at(setter, 6), 0, 0x27) &&
               is_cmp_x_imm(instruction_at(cmp), 0xE) &&
               is_mov_reg(instruction_at(setter, 16), 1, 0) &&
               branch_targets(setter + 17 * sizeof(uint32_t), instruction_at(setter, 17),
                              mapping.base + kFlutterTextRangeResetOffset);
    }

    uintptr_t find_setter_text_limit_cmp(const ExecutableMapping &mapping, uintptr_t setter) {
        const uintptr_t anchor = setter + 14 * sizeof(uint32_t);
        if (contains_range(mapping, anchor, sizeof(uint32_t)) &&
            matches_setter_text_limit_cmp(mapping, anchor)) {
            return anchor;
        }
        constexpr uintptr_t kSearchBefore = 0x200;
        constexpr uintptr_t kSearchAfter = 0x800;
        const uintptr_t start =
                setter > kSearchBefore ? std::max(mapping.start, setter - kSearchBefore)
                                       : mapping.start;
        const uintptr_t end = std::min(mapping.end, setter + kSearchAfter);
        for (uintptr_t cursor = start;
             cursor + sizeof(uint32_t) <= end; cursor += sizeof(uint32_t)) {
            if (is_cmp_x_imm(instruction_at(cursor), 0xE) &&
                matches_setter_text_limit_cmp(mapping, cursor)) {
                return cursor;
            }
        }
        return 0;
    }

    bool matches_controller_text_limit_cmp(const ExecutableMapping &mapping, uintptr_t cmp) {
        if (cmp < mapping.start + 10 * sizeof(uint32_t)) return false;
        const uintptr_t fn = cmp - 10 * sizeof(uint32_t);
        if (!contains_range(mapping, fn, 14 * sizeof(uint32_t)) ||
            !looks_like_flutter_aot_function(mapping, fn))
            return false;
        return is_mov_reg(instruction_at(fn, 3), 0, 2) &&
               is_mov_reg(instruction_at(fn, 5), 2, 3) &&
               is_mov_reg(instruction_at(fn, 7), 3, 1) &&
               is_ldur_x_from(instruction_at(fn, 9), 3, 0x37) &&
               is_cmp_x_imm(instruction_at(cmp), 0xE) &&
               is_mov_reg(instruction_at(fn, 12), 1, 3) &&
               is_bl(instruction_at(fn, 13));
    }

    uintptr_t find_controller_text_limit_cmp(const ExecutableMapping &mapping, uintptr_t around) {
        const uintptr_t anchor =
                mapping.base != 0 ? mapping.base + kFlutterPrivacyWatermarkControllerLimitCmpOffset
                                  : 0;
        if (anchor != 0 && contains_range(mapping, anchor, sizeof(uint32_t)) &&
            matches_controller_text_limit_cmp(mapping, anchor)) {
            return anchor;
        }
        constexpr uintptr_t kSearchBefore = 0x12000;
        constexpr uintptr_t kSearchAfter = 0x2000;
        const uintptr_t start =
                around > kSearchBefore ? std::max(mapping.start, around - kSearchBefore)
                                       : mapping.start;
        const uintptr_t end = std::min(mapping.end, around + kSearchAfter);
        for (uintptr_t cursor = start;
             cursor + sizeof(uint32_t) <= end; cursor += sizeof(uint32_t)) {
            if (is_cmp_x_imm(instruction_at(cursor), 0xE) &&
                matches_controller_text_limit_cmp(mapping, cursor)) {
                return cursor;
            }
        }
        return 0;
    }

    uintptr_t find_formatter_ctor_candidate(const ExecutableMapping &mapping, uintptr_t around) {
        const uintptr_t anchor =
                mapping.base != 0 ? mapping.base + kFlutterPrivacyWatermarkFormatterCtorOffset : 0;
        if (anchor != 0 && contains_range(mapping, anchor, 20 * sizeof(uint32_t))) {
            const uintptr_t limit_mov = anchor + 9 * sizeof(uint32_t);
            if (matches_formatter_limit_sequence(mapping, limit_mov)) {
                reareye::nativehook::log_info(
                        "flutter privacy watermark formatter anchor matched ctor=%p offset=0x%zx limit_mov=%p",
                        reinterpret_cast<void *>(anchor),
                        relative_to_base(mapping, anchor),
                        reinterpret_cast<void *>(limit_mov)
                );
                return anchor;
            }
            reareye::nativehook::log_warn(
                    "flutter privacy watermark formatter anchor mismatch ctor=%p offset=0x%zx i9=0x%08x i10=0x%08x i11=0x%08x i12=0x%08x",
                    reinterpret_cast<void *>(anchor),
                    relative_to_base(mapping, anchor),
                    instruction_at(anchor, 9),
                    instruction_at(anchor, 10),
                    instruction_at(anchor, 11),
                    instruction_at(anchor, 12)
            );
        }

        constexpr uintptr_t kSearchBefore = 0x80000;
        constexpr uintptr_t kSearchAfter = 0x40000;
        const uintptr_t start =
                around > kSearchBefore ? std::max(mapping.start, around - kSearchBefore)
                                       : mapping.start;
        const uintptr_t end = std::min(mapping.end, around + kSearchAfter);
        uintptr_t first_near_maybe = 0;
        for (uintptr_t cursor = start; cursor + 0x30 <= end; cursor += sizeof(uint32_t)) {
            if (!is_movz_x_imm(instruction_at(cursor, 0), 0, 0x1C)) continue;
            if (first_near_maybe == 0) first_near_maybe = cursor;
            if (matches_formatter_limit_sequence(mapping, cursor))
                return cursor - 9 * sizeof(uint32_t);
        }
        reareye::nativehook::log_warn(
                "flutter privacy watermark formatter scan no_match first_near_mov_smi=%p around_offset=0x%zx",
                reinterpret_cast<void *>(first_near_maybe),
                relative_to_base(mapping, around)
        );
        return 0;
    }

    bool make_executable_writable(void *address, size_t length) {
        if (address == nullptr || length == 0) return false;
        long page_size = sysconf(_SC_PAGESIZE);
        if (page_size <= 0) page_size = 4096;
        const uintptr_t start =
                reinterpret_cast<uintptr_t>(address) & ~(static_cast<uintptr_t>(page_size) - 1u);
        const uintptr_t end = (reinterpret_cast<uintptr_t>(address) + length + page_size - 1u) &
                              ~(static_cast<uintptr_t>(page_size) - 1u);
        if (mprotect(reinterpret_cast<void *>(start), end - start,
                     PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
            reareye::nativehook::log_warn(
                    "flutter code mprotect failed address=%p length=%zu errno=%d", address, length,
                    errno);
            return false;
        }
        return true;
    }

    bool patch_instruction(uintptr_t address, uint32_t instruction) {
        if (!make_executable_writable(reinterpret_cast<void *>(address), sizeof(uint32_t)))
            return false;
        *reinterpret_cast<uint32_t *>(address) = instruction;
        __builtin___clear_cache(reinterpret_cast<char *>(address),
                                reinterpret_cast<char *>(address + sizeof(uint32_t)));
        return true;
    }

    std::vector<uintptr_t> find_reset_helper_candidates(const ExecutableMapping &mapping) {
        std::vector<uintptr_t> candidates;
        if (mapping.start == 0 || mapping.end <= mapping.start) return candidates;
        for (uintptr_t cursor = mapping.start;
             cursor + 72 <= mapping.end; cursor += sizeof(uint32_t)) {
            if (matches_reset_helper(mapping, cursor)) candidates.push_back(cursor);
        }
        return candidates;
    }

    uintptr_t find_setter_candidate(const ExecutableMapping &mapping, uintptr_t reset) {
        for (uintptr_t cursor = mapping.start;
             cursor + 0x90 <= mapping.end; cursor += sizeof(uint32_t)) {
            if (matches_setter_candidate(mapping, cursor, reset)) return cursor;
        }
        return 0;
    }

    uintptr_t find_append_candidate_in_range(const ExecutableMapping &mapping, uintptr_t reset,
                                             uintptr_t start, uintptr_t end) {
        start = std::max(start, mapping.start);
        end = std::min(end, mapping.end);
        start &= ~static_cast<uintptr_t>(sizeof(uint32_t) - 1u);
        for (uintptr_t cursor = start; cursor + 0x120 <= end; cursor += sizeof(uint32_t)) {
            if (matches_append_candidate(mapping, cursor, reset)) return cursor;
        }
        return 0;
    }

    uintptr_t
    find_append_candidate(const ExecutableMapping &mapping, uintptr_t reset, uintptr_t setter) {
        constexpr uintptr_t kNearbyBefore = 0x4000;
        constexpr uintptr_t kNearbyAfter = 0x8000;
        const uintptr_t nearby_start =
                setter > kNearbyBefore ? setter - kNearbyBefore : mapping.start;
        const uintptr_t nearby_end =
                setter + kNearbyAfter > setter ? setter + kNearbyAfter : mapping.end;
        uintptr_t append = find_append_candidate_in_range(mapping, reset, nearby_start, nearby_end);
        if (append != 0) return append;
        return find_append_candidate_in_range(mapping, reset, mapping.start, mapping.end);
    }

    bool locate_flutter_watermark_hook_targets(const ExecutableMapping &mapping,
                                               FlutterWatermarkHookTargets *out) {
        if (out == nullptr) return false;
        auto resets = find_reset_helper_candidates(mapping);
        reareye::nativehook::log_info(
                "flutter privacy watermark scan reset_candidates=%zu text=%p-%p base=%p path=%s",
                resets.size(),
                reinterpret_cast<void *>(mapping.start),
                reinterpret_cast<void *>(mapping.end),
                reinterpret_cast<void *>(mapping.base),
                mapping.path.empty() ? "<unknown>" : mapping.path.c_str()
        );
        for (size_t index = 0; index < resets.size() && index < 8; ++index) {
            reareye::nativehook::log_info(
                    "flutter privacy watermark reset candidate[%zu]=%p offset=0x%zx",
                    index,
                    reinterpret_cast<void *>(resets[index]),
                    relative_to_base(mapping, resets[index])
            );
        }

        for (uintptr_t reset: resets) {
            const uintptr_t setter = find_setter_candidate(mapping, reset);
            if (setter == 0) continue;
            const uintptr_t append = find_append_candidate(mapping, reset, setter);
            if (append == 0 || append == setter) {
                reareye::nativehook::log_warn(
                        "flutter privacy watermark scan append missing reset=%p setter=%p",
                        reinterpret_cast<void *>(reset),
                        reinterpret_cast<void *>(setter)
                );
                continue;
            }
            const uintptr_t formatter_ctor = find_formatter_ctor_candidate(mapping, setter);
            if (formatter_ctor == 0) {
                reareye::nativehook::log_warn(
                        "flutter privacy watermark scan formatter ctor missing reset=%p setter=%p append=%p",
                        reinterpret_cast<void *>(reset),
                        reinterpret_cast<void *>(setter),
                        reinterpret_cast<void *>(append)
                );
            }
            const uintptr_t setter_text_limit_cmp = find_setter_text_limit_cmp(mapping, setter);
            const uintptr_t controller_text_limit_cmp = find_controller_text_limit_cmp(mapping,
                                                                                       setter);
            if (setter_text_limit_cmp == 0 || controller_text_limit_cmp == 0) {
                reareye::nativehook::log_warn(
                        "flutter privacy watermark scan limit cmp partial setter_cmp=%p controller_cmp=%p setter=%p append=%p",
                        reinterpret_cast<void *>(setter_text_limit_cmp),
                        reinterpret_cast<void *>(controller_text_limit_cmp),
                        reinterpret_cast<void *>(setter),
                        reinterpret_cast<void *>(append)
                );
            }
            *out = {
                    reset,
                    setter,
                    append,
                    formatter_ctor,
                    formatter_ctor != 0 ? formatter_ctor + 11 * sizeof(uint32_t) : 0,
                    formatter_ctor != 0 ? formatter_ctor + 9 * sizeof(uint32_t) : 0,
                    setter_text_limit_cmp,
                    controller_text_limit_cmp,
            };
            return true;
        }
        return false;
    }

#else

    bool locate_flutter_watermark_hook_targets(const ExecutableMapping &, FlutterWatermarkHookTargets *) {
        return false;
    }

#endif

    uint64_t
    replacement_flutter_privacy_watermark_text_setter(uint64_t arg0, uint64_t arg1, uint64_t arg2) {
        // This Flutter AOT method enforces the 14-character limit by calling the small reset helper
        // when textLength > 14. Replacing the whole method with the adjacent append/update path keeps
        // normal text updates while avoiding the reset branch.
        if (original_flutter_privacy_watermark_text_append != nullptr) {
            return original_flutter_privacy_watermark_text_append(arg0, arg1, arg2);
        }
        return 0;
    }

    void install_flutter_app_hooks(void *, const char *source) {
        if (!config.enable_privacy_watermark) return;
        if (flutter_app_hooks_installed.load(std::memory_order_acquire)) return;

        // libapp.so 名字不唯一（任何 Flutter 应用都叫这个），走到这里前必须已通过
        // native_init 的进程身份甄别（process_target()==Gallery）。
        if (reareye::nativehook::process_target() != ProcessTarget::Gallery) {
            reareye::nativehook::log_warn(
                    "flutter libapp.so hook rejected: process identity mismatch target=%s",
                    reareye::nativehook::process_target_name(
                            reareye::nativehook::process_target()));
            return;
        }

        ExecutableMapping mapping{};
        bool mapped = false;
        // 回调到达时 Flutter 运行时可能尚未完成映射；短重试避免漏装。
        for (int attempt = 1; attempt <= kFlutterMappingRetryAttempts; ++attempt) {
            if (find_executable_mapping(kFlutterAppLibraryName, &mapping)) {
                mapped = true;
                break;
            }
            usleep(kFlutterMappingRetryIntervalUs);
        }
        if (!mapped) {
            reareye::nativehook::log_warn(
                    "flutter app load observed but executable mapping missing source=%s privacy_watermark=%d",
                    source != nullptr ? source : "<unknown>",
                    config.enable_privacy_watermark
            );
            return;
        }

        reareye::nativehook::log_info(
                "flutter app load observed source=%s base=%p text=%p-%p privacy_watermark=%d",
                source != nullptr ? source : "<unknown>",
                reinterpret_cast<void *>(mapping.base),
                reinterpret_cast<void *>(mapping.start),
                reinterpret_cast<void *>(mapping.end),
                config.enable_privacy_watermark
        );

        FlutterWatermarkHookTargets targets{};
        if (!locate_flutter_watermark_hook_targets(mapping, &targets)) {
            reareye::nativehook::log_warn(
                    "flutter privacy watermark text hook unsupported: pattern scan failed base=%p text=%p-%p fixed_setter=0x%zx fixed_append=0x%zx fixed_reset=0x%zx",
                    reinterpret_cast<void *>(mapping.base),
                    reinterpret_cast<void *>(mapping.start),
                    reinterpret_cast<void *>(mapping.end),
                    static_cast<size_t>(kFlutterPrivacyWatermarkTextSetterOffset),
                    static_cast<size_t>(kFlutterPrivacyWatermarkTextAppendOffset),
                    static_cast<size_t>(kFlutterTextRangeResetOffset)
            );
            return;
        }

        if (!contains_address(mapping, targets.setter) ||
            !contains_address(mapping, targets.append) ||
            !contains_address(mapping, targets.reset) || targets.setter == targets.append) {
            reareye::nativehook::log_warn(
                    "flutter privacy watermark text hook rejected reset=%p setter=%p append=%p text=%p-%p",
                    reinterpret_cast<void *>(targets.reset),
                    reinterpret_cast<void *>(targets.setter),
                    reinterpret_cast<void *>(targets.append),
                    reinterpret_cast<void *>(mapping.start),
                    reinterpret_cast<void *>(mapping.end)
            );
            return;
        }

        bool expected = false;
        if (!flutter_app_hooks_installed.compare_exchange_strong(expected, true,
                                                                 std::memory_order_acq_rel))
            return;

        int formatter_patch_status = -1;
        int setter_limit_patch_status = -1;
        int controller_limit_patch_status = -1;
#if defined(__aarch64__)
        if (targets.formatter_smi_limit_mov != 0 && targets.formatter_text_limit_cmp != 0) {
            const uint32_t smi_limit = kFlutterPrivacyWatermarkMaxChars * 2u;
            const bool patched_text_limit = patch_instruction(
                    targets.formatter_text_limit_cmp,
                    encode_movz_x_imm(0, smi_limit)
            );
            const bool patched_smi_limit = patch_instruction(
                    targets.formatter_smi_limit_mov,
                    encode_movz_x_imm(16, kFlutterPrivacyWatermarkMaxChars)
            );
            formatter_patch_status = patched_text_limit && patched_smi_limit ? 0 : -2;
        }
        if (targets.setter_text_length_cmp != 0) {
            const uint32_t old_instruction = instruction_at(targets.setter_text_length_cmp);
            setter_limit_patch_status = patch_instruction(
                    targets.setter_text_length_cmp,
                    encode_cmp_x_imm(old_instruction, kFlutterPrivacyWatermarkMaxChars)
            ) ? 0 : -2;
        }
        if (targets.controller_text_length_cmp != 0) {
            const uint32_t old_instruction = instruction_at(targets.controller_text_length_cmp);
            controller_limit_patch_status = patch_instruction(
                    targets.controller_text_length_cmp,
                    encode_cmp_x_imm(old_instruction, kFlutterPrivacyWatermarkMaxChars)
            ) ? 0 : -2;
        }
#endif

        void *setter = reinterpret_cast<void *>(targets.setter);
        void *append = reinterpret_cast<void *>(targets.append);
        original_flutter_privacy_watermark_text_append = reinterpret_cast<FlutterTextSetterFn>(append);
        const int status = reareye::nativehook::inline_hook_arm64(
                setter,
                reinterpret_cast<void *>(replacement_flutter_privacy_watermark_text_setter),
                reinterpret_cast<void **>(&original_flutter_privacy_watermark_text_setter)
        );
        if (status != 0) {
            flutter_app_hooks_installed.store(false, std::memory_order_release);
        }
        reareye::nativehook::log_info(
                "flutter privacy watermark text hook status=%d formatter_patch=%d setter_limit_patch=%d controller_limit_patch=%d setter=%p append=%p reset=%p formatter_ctor=%p setter_cmp=%p controller_cmp=%p setter_offset=0x%zx append_offset=0x%zx reset_offset=0x%zx formatter_offset=0x%zx setter_cmp_offset=0x%zx controller_cmp_offset=0x%zx max_chars=%u",
                status,
                formatter_patch_status,
                setter_limit_patch_status,
                controller_limit_patch_status,
                setter,
                append,
                reinterpret_cast<void *>(targets.reset),
                reinterpret_cast<void *>(targets.formatter_ctor),
                reinterpret_cast<void *>(targets.setter_text_length_cmp),
                reinterpret_cast<void *>(targets.controller_text_length_cmp),
                relative_to_base(mapping, targets.setter),
                relative_to_base(mapping, targets.append),
                relative_to_base(mapping, targets.reset),
                targets.formatter_ctor != 0 ? relative_to_base(mapping, targets.formatter_ctor) : 0,
                targets.setter_text_length_cmp != 0 ? relative_to_base(mapping,
                                                                       targets.setter_text_length_cmp)
                                                    : 0,
                targets.controller_text_length_cmp != 0 ? relative_to_base(mapping,
                                                                           targets.controller_text_length_cmp)
                                                        : 0,
                kFlutterPrivacyWatermarkMaxChars
        );
    }

    uint64_t replacement_media_editor_available() {
        if (!has_media_editor_feature_enabled()) {
            return original_media_editor_available != nullptr ? original_media_editor_available()
                                                              : 0;
        }
        log_replacement_once("is_media_editor_available", "result=1 native");
        return 1;
    }

    int64_t replacement_media_editor_api_version() {
        if (!has_media_editor_feature_enabled()) {
            return original_media_editor_api_version != nullptr
                   ? original_media_editor_api_version() : -1;
        }
        constexpr int64_t kForcedMediaEditorApiVersion = 1000000;
        log_replacement_once("get_media_editor_api_for_gallery_version_code",
                             "result=" + std::to_string(kForcedMediaEditorApiVersion) + " native");
        return kForcedMediaEditorApiVersion;
    }

    uint64_t replacement_build_util_is_global(int64_t self) {
        if (config.backup_server == 0) {
            return original_build_util_is_global != nullptr ? original_build_util_is_global(self)
                                                            : 0;
        }
        const uint64_t result = config.backup_server == 2 ? 1 : 0;
        log_replacement_once("BuildUtil::is_global",
                             "backup_server=" + std::to_string(config.backup_server) + " result=" +
                             std::to_string(result) + " no_trampoline");
        return result;
    }

    uint64_t replacement_ai_support_use_algo() {
        if (!has_ai_feature_enabled()) {
            return original_ai_support_use_algo != nullptr ? original_ai_support_use_algo() : 0;
        }
        log_replacement_once("is_support_use_ai_core_algo", "result=1 no_trampoline");
        return 1;
    }

    uint64_t replacement_ai_support_full_search() {
        if (!has_ai_feature_enabled()) {
            return original_ai_support_full_search != nullptr ? original_ai_support_full_search()
                                                              : 0;
        }
        log_replacement_once("is_support_ai_core_full_search", "result=1 no_trampoline");
        return 1;
    }

    uint64_t replacement_ai_check_algo_support(int32_t algo_type) {
        if (!has_ai_feature_enabled()) {
            return original_ai_check_algo_support != nullptr ? original_ai_check_algo_support(
                    algo_type) : 0;
        }
        log_replacement_once("check_algo_support",
                             "algo=" + std::to_string(algo_type) + " result=1 no_trampoline");
        return 1;
    }

    uint64_t replacement_search_clip_support(int64_t self) {
        if (!has_ai_feature_enabled()) {
            return original_search_clip_support != nullptr ? original_search_clip_support(self) : 0;
        }
        log_replacement_once("SearchClipManager::is_support_clip", "result=1 no_trampoline");
        return 1;
    }

    uint64_t replacement_trash_bin_start_ms() {
        if (!config.longer_trashbin_time) {
            return original_trash_bin_start_ms != nullptr ? original_trash_bin_start_ms() : 0;
        }
        const uint64_t retention_ms =
                static_cast<uint64_t>(config.trash_retention_days) * 24ull * 60ull * 60ull *
                1000ull;
        const uint64_t result = now_ms() > retention_ms ? now_ms() - retention_ms : 0;
        log_replacement_once("TrashUtil::get_trash_bin_start_ms",
                             "days=" + std::to_string(config.trash_retention_days) + " result=" +
                             std::to_string(result) + " no_trampoline");
        return result;
    }

    uint64_t replacement_support_backup_only_wifi() {
        if (config.backup_server == 0) {
            return original_support_backup_only_wifi != nullptr
                   ? original_support_backup_only_wifi() : 0;
        }
        log_replacement_once("is_support_backup_only_wifi",
                             "backup_server=" + std::to_string(config.backup_server) +
                             " result=1 no_trampoline");
        return 1;
    }

    uint8_t replacement_feature_parser_has_feature(const char *key, int32_t key_len,
                                                   int32_t default_value) {
        const FeatureKey *feature_key = matched_feature_key(key, key_len);
        if (feature_key != nullptr && is_group_enabled(feature_key->groups)) {
            log_replacement_once("Feature_parser_has_feature",
                                 key_detail(key, key_len) + " result=1 no_original");
            return 1;
        }
        return original_feature_parser_has_feature != nullptr
               ? original_feature_parser_has_feature(key, key_len, default_value)
               : static_cast<uint8_t>(default_value != 0);
    }

    uint8_t replacement_feature_parser_get_boolean(const char *key, int32_t key_len,
                                                   int32_t default_value) {
        const FeatureKey *feature_key = matched_feature_key(key, key_len);
        if (feature_key != nullptr && is_group_enabled(feature_key->groups)) {
            log_replacement_once("Feature_parser_get_boolean",
                                 key_detail(key, key_len) + " result=1 no_original");
            return 1;
        }
        return original_feature_parser_get_boolean != nullptr
               ? original_feature_parser_get_boolean(key, key_len, default_value)
               : static_cast<uint8_t>(default_value != 0);
    }

    uint8_t replacement_miui_os_build_is_international_build() {
        const uint8_t original = original_miui_os_build_is_international_build != nullptr
                                 ? original_miui_os_build_is_international_build() : 0;
        if (config.backup_server == 0) return original;
        const uint8_t result = config.backup_server == 2 ? 1 : 0;
        log_replacement_once("MiuiOsBuild_is_international_build",
                             "backup_server=" + std::to_string(config.backup_server) +
                             " original=" + std::to_string(original) + " result=" +
                             std::to_string(result));
        return result;
    }

    uint8_t replacement_miui_os_build_is_global_build() {
        const uint8_t original = original_miui_os_build_is_global_build != nullptr
                                 ? original_miui_os_build_is_global_build() : 0;
        if (config.backup_server == 0) return original;
        const uint8_t result = config.backup_server == 2 ? 1 : 0;
        log_replacement_once("MiuiOsBuild_is_global_build",
                             "backup_server=" + std::to_string(config.backup_server) +
                             " original=" + std::to_string(original) + " result=" +
                             std::to_string(result));
        return result;
    }

    void install_gallery_hooks() {
        bool expected = false;
        if (!gallery_hooks_installed.compare_exchange_strong(expected, true)) {
            reareye::nativehook::log_info("gallery hooks already installed, skip");
            return;
        }

        load_config();

        struct ImportTarget {
            const char *name;
            void *replacement;
            void **backup;
            bool enabled;
        };
        const bool has_parser_features =
                config.enable_hdr_enhanced || config.enable_pdf || config.enable_ocr ||
                config.enable_ocr_form || has_creation_feature_enabled() ||
                config.enable_video_editor ||
                config.enable_print || config.enable_privacy_watermark;
        ImportTarget import_targets[] = {
                {kFeatureParserHasFeatureSymbol,         reinterpret_cast<void *>(replacement_feature_parser_has_feature),           reinterpret_cast<void **>(&original_feature_parser_has_feature), has_parser_features},
                {kFeatureParserGetBooleanSymbol,         reinterpret_cast<void *>(replacement_feature_parser_get_boolean),           reinterpret_cast<void **>(&original_feature_parser_get_boolean), has_parser_features},
                {kMiuiOsBuildIsInternationalBuildSymbol, reinterpret_cast<void *>(replacement_miui_os_build_is_international_build), reinterpret_cast<void **>(&original_miui_os_build_is_international_build),
                                                                                                                                                                                                      config.backup_server !=
                                                                                                                                                                                                      0},
                {kMiuiOsBuildIsGlobalBuildSymbol,        reinterpret_cast<void *>(replacement_miui_os_build_is_global_build),        reinterpret_cast<void **>(&original_miui_os_build_is_global_build),
                                                                                                                                                                                                      config.backup_server !=
                                                                                                                                                                                                      0},
        };
        std::vector<reareye::nativehook::SymbolHook> import_hooks;
        for (const auto &target: import_targets) {
            if (target.enabled) {
                import_hooks.push_back({target.name, target.replacement, target.backup});
            }
        }
        if (!import_hooks.empty()) {
            auto import_result = reareye::nativehook::hook_import_symbols(kOriginalLibraryName,
                                                                          import_hooks.data(),
                                                                          import_hooks.size());
            reareye::nativehook::log_info(
                    "gallery import hooks install %s requested=%zu installed=%zu missing=%zu failed=%zu",
                    import_result.installed == import_result.requested ? "complete" : "partial",
                    import_result.requested,
                    import_result.installed,
                    import_result.missing,
                    import_result.failed
            );
        } else {
            reareye::nativehook::log_info("gallery import hooks skipped reason=no_enabled_feature");
        }

        struct InlineTarget {
            const char *name;
            const char *symbol;
            uintptr_t offset;
            void *replacement;
            void **backup;
            bool enabled;
        };
        InlineTarget inline_targets[] = {
                {"is_media_editor_available",                     kMediaEditorAvailableSymbol,  kMediaEditorAvailableOffset,  reinterpret_cast<void *>(replacement_media_editor_available),   reinterpret_cast<void **>(&original_media_editor_available),   has_media_editor_feature_enabled()},
                {"get_media_editor_api_for_gallery_version_code", kMediaEditorApiVersionSymbol, kMediaEditorApiVersionOffset, reinterpret_cast<void *>(replacement_media_editor_api_version), reinterpret_cast<void **>(&original_media_editor_api_version), has_media_editor_feature_enabled()},
                {"BuildUtil::is_global",                          kBuildUtilIsGlobalSymbol,     kBuildUtilIsGlobalOffset,     reinterpret_cast<void *>(replacement_build_util_is_global),     reinterpret_cast<void **>(&original_build_util_is_global),     config.backup_server !=
                                                                                                                                                                                                                                                             0},
                {"is_support_use_ai_core_algo",                   kAiSupportUseAlgoSymbol,      kAiSupportUseAlgoOffset,      reinterpret_cast<void *>(replacement_ai_support_use_algo),      reinterpret_cast<void **>(&original_ai_support_use_algo),      has_ai_feature_enabled()},
                {"is_support_ai_core_full_search",                kAiSupportFullSearchSymbol,   kAiSupportFullSearchOffset,   reinterpret_cast<void *>(replacement_ai_support_full_search),   reinterpret_cast<void **>(&original_ai_support_full_search),   has_ai_feature_enabled()},
                {"check_algo_support",                            kAiCheckAlgoSupportSymbol,    kAiCheckAlgoSupportOffset,    reinterpret_cast<void *>(replacement_ai_check_algo_support),    reinterpret_cast<void **>(&original_ai_check_algo_support),    has_ai_feature_enabled()},
                {"SearchClipManager::is_support_clip",            kSearchClipSupportSymbol,     kSearchClipSupportOffset,     reinterpret_cast<void *>(replacement_search_clip_support),      reinterpret_cast<void **>(&original_search_clip_support),      has_ai_feature_enabled()},
                {"TrashUtil::get_trash_bin_start_ms",             kTrashBinStartMsSymbol,       kTrashBinStartMsOffset,       reinterpret_cast<void *>(replacement_trash_bin_start_ms),       reinterpret_cast<void **>(&original_trash_bin_start_ms),       config.longer_trashbin_time},
                {"is_support_backup_only_wifi",                   kSupportBackupOnlyWifiSymbol, kSupportBackupOnlyWifiOffset, reinterpret_cast<void *>(replacement_support_backup_only_wifi), reinterpret_cast<void **>(&original_support_backup_only_wifi), config.backup_server !=
                                                                                                                                                                                                                                                             0},
        };

        size_t inline_enabled_count = 0;
        for (const auto &target: inline_targets) {
            if (!target.enabled) continue;
            ++inline_enabled_count;
            void *address = reareye::nativehook::find_library_symbol_or_offset(
                    original_library_handle, kOriginalLibraryName, target.symbol, target.offset);
            if (address == nullptr) {
                reareye::nativehook::log_warn(
                        "gallery inline hook missing name=%s symbol=%s offset=0x%zx", target.name,
                        target.symbol, static_cast<size_t>(target.offset));
                continue;
            }
            const int status = reareye::nativehook::inline_hook_arm64(address, target.replacement,
                                                                      target.backup);
            reareye::nativehook::log_info(
                    "gallery inline hook name=%s status=%d target=%p replacement=%p backup=%p offset=0x%zx",
                    target.name,
                    status,
                    address,
                    target.replacement,
                    target.backup != nullptr ? *target.backup : nullptr,
                    static_cast<size_t>(target.offset)
            );
        }
        if (inline_enabled_count == 0) {
            reareye::nativehook::log_info("gallery inline hooks skipped reason=no_enabled_feature");
        }
    }

} // namespace

namespace {

    // C++17 下 std::string_view 没有 ends_with。
    bool ends_with(std::string_view text, std::string_view suffix) {
        return text.size() >= suffix.size() &&
               text.compare(text.size() - suffix.size(), suffix.size(), suffix) == 0;
    }

} // namespace

// LSPosed Native Hook 协议入口。
// HyperOS Runtime 进程没有 ART：LSPosed 依据模块 assets/native_init 自动加载本库并调用
// native_init，全程不经过 Java。进程身份不匹配时返回 nullptr，不参与宿主生命周期；
// 因此任意第三方 Flutter 应用的 libapp.so 加载事件都会被进程甄别拦截。
extern "C" [[gnu::visibility("default")]] [[gnu::used]]
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries) {
    reareye::nativehook::set_log_tag(kLogTag);
    return reareye::nativehook::init_native(entries, [](const char *name, void *handle) {
        if (reareye::nativehook::process_target() != ProcessTarget::Gallery) return;
        if (name == nullptr) return;
        const std::string_view loaded(name);
        if (ends_with(loaded, "/libapp_gallery.so")) {
            original_library_handle = handle;
            reareye::nativehook::log_info("library loaded name=%s handle=%p", name, handle);
            install_gallery_hooks();
            return;
        }
        if (ends_with(loaded, "/libapp.so")) {
            // 已通过进程身份甄别，这里必然是相册自己的 Flutter 运行时。
            install_flutter_app_hooks(handle, "lspd-callback");
            return;
        }
    });
}
