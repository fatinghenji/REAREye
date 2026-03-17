package hk.uwu.reareye.ui.config

import hk.uwu.reareye.R

object ConfigKeys {
    const val HOOK_ACTIVITIES_WHITELIST = "enable_activities_whitelist_hook"
    const val ACTIVITIES_WHITELIST_APPS = "activities_whitelist_apps"

    const val HOOK_MUSIC_CONTROLS_WHITELIST = "enable_music_controls_whitelist_hook"
    const val MUSIC_CONTROLS_WHITELIST_APPS = "music_controls_whitelist_apps"
    const val HOOK_MUSIC_CONTROLS_FORCE_UPDATE = "enable_music_controls_force_update"

    const val HOOK_BACKGROUND_WHITELIST = "enable_background_whitelist_hook"
    const val BACKGROUND_WHITELIST_APPS = "background_whitelist_apps"
    const val BACKGROUND_LOCK_APPS = "background_lock_apps"

    const val MISC_HOOK_GMS_UNLOCK = "enable_misc_gms_unlock"

    const val HOOK_UNLOCK_VIDEO_RESTRICTIONS = "enable_unlock_video_restrictions"
}

val REAREyeConfig = listOf(
    ConfigCategory(
        key = "system_framework",
        titleRes = R.string.category_system,
        subCategories = listOf(
            ConfigCategory(
                key = "activities_whitelist",
                titleRes = R.string.cfg_activities_whitelist,
                descriptionRes = R.string.cfg_activities_whitelist_desc,
                items = listOf(
                    ConfigItem(
                        key = ConfigKeys.HOOK_ACTIVITIES_WHITELIST,
                        titleRes = R.string.enable_custom_activities_whitelist,
                        type = ConfigType.BooleanVal(defaultValue = true)
                    ),
                    ConfigItem(
                        key = ConfigKeys.ACTIVITIES_WHITELIST_APPS,
                        titleRes = R.string.custom_activities_whitelist_apps,
                        descriptionRes = R.string.custom_activities_whitelist_apps_desc,
                        type = ConfigType.AppList(defaultValues = emptySet())
                    )
                )
            ),
            ConfigCategory(
                key = "background_whitelist",
                titleRes = R.string.cfg_background_whitelist,
                descriptionRes = R.string.cfg_background_whitelist_desc,
                items = listOf(
                    ConfigItem(
                        key = ConfigKeys.HOOK_BACKGROUND_WHITELIST,
                        titleRes = R.string.enable_background_whitelist,
                        descriptionRes = R.string.enable_background_whitelist_desc,
                        type = ConfigType.BooleanVal(defaultValue = true)
                    ),
                    ConfigItem(
                        key = ConfigKeys.BACKGROUND_WHITELIST_APPS,
                        titleRes = R.string.background_whitelist_apps,
                        descriptionRes = R.string.background_whitelist_apps_desc,
                        type = ConfigType.AppList(defaultValues = emptySet())
                    ),
                    ConfigItem(
                        key = ConfigKeys.BACKGROUND_LOCK_APPS,
                        titleRes = R.string.background_lock_apps,
                        descriptionRes = R.string.background_lock_apps_desc,
                        type = ConfigType.AppList(defaultValues = emptySet())
                    ),
                )
            ),
            ConfigCategory(
                key = "system_misc",
                titleRes = R.string.subcategory_misc,
                descriptionRes = R.string.subcategory_misc_desc,
                items = listOf(
                    ConfigItem(
                        key = ConfigKeys.MISC_HOOK_GMS_UNLOCK,
                        titleRes = R.string.enable_misc_unlock_gms,
                        type = ConfigType.BooleanVal(defaultValue = false)
                    )
                )
            )
        )
    ),
    ConfigCategory(
        key = "subscreen_center",
        titleRes = R.string.category_subscreencenter,
        subCategories = listOf(
            ConfigCategory(
                key = "music_controls_whitelist",
                titleRes = R.string.cfg_music_control_whitelist,
                descriptionRes = R.string.cfg_music_control_whitelist_desc,
                items = listOf(
                    ConfigItem(
                        key = ConfigKeys.HOOK_MUSIC_CONTROLS_WHITELIST,
                        titleRes = R.string.enable_music_control_whitelist,
                        type = ConfigType.BooleanVal(defaultValue = true)
                    ),
                    ConfigItem(
                        key = ConfigKeys.MUSIC_CONTROLS_WHITELIST_APPS,
                        titleRes = R.string.music_control_whitelist_apps,
                        descriptionRes = R.string.music_control_whitelist_apps_desc,
                        type = ConfigType.AppList(defaultValues = emptySet())
                    ),
                    ConfigItem(
                        key = ConfigKeys.HOOK_MUSIC_CONTROLS_FORCE_UPDATE,
                        titleRes = R.string.enable_music_control_force_update,
                        descriptionRes = R.string.enable_music_control_force_update_desc,
                        type = ConfigType.BooleanVal(defaultValue = false)
                    )
                )
            )
        )
    ),
    ConfigCategory(
        key = "theme_manager",
        titleRes = R.string.category_thememanager,
        subCategories = listOf(
            ConfigCategory(
                key = "video_restrictions",
                titleRes = R.string.cfg_unlock_video_restrictions,
                descriptionRes = R.string.cfg_unlock_video_restrictions_desc,
                items = listOf(
                    ConfigItem(
                        ConfigKeys.HOOK_UNLOCK_VIDEO_RESTRICTIONS,
                        titleRes = R.string.common_cfg_enable,
                        type = ConfigType.BooleanVal(defaultValue = true)
                    )
                )
            )
        )
    )
)
