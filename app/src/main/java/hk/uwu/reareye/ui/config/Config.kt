package hk.uwu.reareye.ui.config

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import hk.uwu.reareye.ui.components.config.AppListConfigInput
import hk.uwu.reareye.ui.components.config.BooleanConfigInput

sealed class ConfigType {
    @Composable
    abstract fun RenderInput(
        item: ConfigItem,
        prefsManager: PrefsManager,
        onOpenAppList: (ConfigItem) -> Unit
    )

    open val defaultStringSet: Set<String> = emptySet()

    data class BooleanVal(val defaultValue: Boolean = false) : ConfigType() {
        @Composable
        override fun RenderInput(
            item: ConfigItem,
            prefsManager: PrefsManager,
            onOpenAppList: (ConfigItem) -> Unit
        ) {
            BooleanConfigInput(
                item = item,
                defaultValue = defaultValue,
                prefsManager = prefsManager
            )
        }
    }

    data class AppList(val defaultValues: Set<String> = emptySet()) : ConfigType() {
        override val defaultStringSet: Set<String>
            get() = defaultValues

        @Composable
        override fun RenderInput(
            item: ConfigItem,
            prefsManager: PrefsManager,
            onOpenAppList: (ConfigItem) -> Unit
        ) {
            AppListConfigInput(
                item = item,
                defaultValues = defaultValues,
                prefsManager = prefsManager,
                onClick = { onOpenAppList(item) }
            )
        }
    }

    // Additional types like StringVal, IntVal can be added here
}

sealed interface ConfigNode {
    val key: String?

    @get:StringRes
    val titleRes: Int?

    @get:StringRes
    val descriptionRes: Int?
}

data class ConfigItem(
    override val key: String,
    @param:StringRes override val titleRes: Int,
    @param:StringRes override val descriptionRes: Int? = null,
    val type: ConfigType
) : ConfigNode

data class ConfigCategory(
    override val key: String? = null,
    @param:StringRes override val titleRes: Int,
    @param:StringRes override val descriptionRes: Int? = null,
    val icon: ConfigCategoryIcon? = null,
    val children: List<ConfigNode> = emptyList()
) : ConfigNode

data class ConfigGroup(
    override val key: String? = null,
    @param:StringRes override val titleRes: Int? = null,
    @param:StringRes override val descriptionRes: Int? = null,
    val children: List<ConfigNode> = emptyList()
) : ConfigNode

sealed interface ConfigCategoryIcon {
    data class Package(val packageName: String) : ConfigCategoryIcon
    data class Compose(val imageVector: ImageVector) : ConfigCategoryIcon
}
