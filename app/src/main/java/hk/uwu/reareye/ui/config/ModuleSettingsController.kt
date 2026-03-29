package hk.uwu.reareye.ui.config

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object ModuleSettingsController {
    private const val LAUNCHER_ALIAS_SUFFIX = ".ui.MainActivityAlias"

    fun syncLauncherEntryVisibility(context: Context, hidden: Boolean) {
        val packageManager = context.packageManager
        val aliasComponent = ComponentName(context, context.packageName + LAUNCHER_ALIAS_SUFFIX)

        val hasAlias = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getActivityInfo(
                    aliasComponent,
                    PackageManager.ComponentInfoFlags.of(
                        PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(
                    aliasComponent,
                    PackageManager.MATCH_DISABLED_COMPONENTS,
                )
            }
        }.isSuccess
        if (!hasAlias) return

        val currentlyEnabled = when (packageManager.getComponentEnabledSetting(aliasComponent)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false

            else -> true
        }

        val shouldEnable = !hidden
        if (currentlyEnabled == shouldEnable) return

        packageManager.setComponentEnabledSetting(
            aliasComponent,
            if (shouldEnable) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}
