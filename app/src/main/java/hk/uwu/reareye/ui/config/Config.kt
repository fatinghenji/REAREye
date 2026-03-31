package hk.uwu.reareye.ui.config

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import hk.uwu.reareye.ui.components.config.AppListConfigInput
import hk.uwu.reareye.ui.components.config.BooleanConfigInput
import hk.uwu.reareye.ui.components.config.ManagerConfigInput
import hk.uwu.reareye.ui.components.config.MaskMultiSelectConfigInput

sealed class ConfigType {
    @Composable
    abstract fun RenderInput(
        item: ConfigItem,
        prefsManager: PrefsManager,
        onOpenAppList: (ConfigItem) -> Unit,
        onOpenManager: (ConfigItem) -> Unit,
    )

    open val defaultStringSet: Set<String> = emptySet()

    data class MaskOption(
        @param:StringRes val titleRes: Int,
        val maskValue: Int,
    )

    data class BooleanVal(val defaultValue: Boolean = false) : ConfigType() {
        @Composable
        override fun RenderInput(
            item: ConfigItem,
            prefsManager: PrefsManager,
            onOpenAppList: (ConfigItem) -> Unit,
            onOpenManager: (ConfigItem) -> Unit,
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
            onOpenAppList: (ConfigItem) -> Unit,
            onOpenManager: (ConfigItem) -> Unit,
        ) {
            AppListConfigInput(
                item = item,
                defaultValues = defaultValues,
                prefsManager = prefsManager,
                onClick = { onOpenAppList(item) }
            )
        }
    }

    data class MaskMultiSelect(
        val defaultValue: Int,
        val options: List<MaskOption>,
    ) : ConfigType() {
        @Composable
        override fun RenderInput(
            item: ConfigItem,
            prefsManager: PrefsManager,
            onOpenAppList: (ConfigItem) -> Unit,
            onOpenManager: (ConfigItem) -> Unit,
        ) {
            MaskMultiSelectConfigInput(
                item = item,
                defaultValue = defaultValue,
                options = options,
                prefsManager = prefsManager,
            )
        }
    }

    enum class ManagerType {
        BUSINESS,
        CARD,
    }

    data class Manager(val managerType: ManagerType) : ConfigType() {
        @Composable
        override fun RenderInput(
            item: ConfigItem,
            prefsManager: PrefsManager,
            onOpenAppList: (ConfigItem) -> Unit,
            onOpenManager: (ConfigItem) -> Unit,
        ) {
            ManagerConfigInput(
                item = item,
                onClick = { onOpenManager(item) }
            )
        }
    }

    // Additional types like StringVal, IntVal can be added here.
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
