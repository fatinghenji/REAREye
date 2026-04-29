package hk.uwu.reareye.ui.components.config.template

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hk.uwu.reareye.R
import hk.uwu.reareye.repository.rearwallpaper.RearWallpaperInfo
import hk.uwu.reareye.repository.rearwallpaper.RearWallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WidgetTemplateConfigScreen(
    business: String,
    sourceFilePath: String,
    cardStorageKey: String,
    currentConfigJson: String?,
    onBack: () -> Unit,
    onSave: (String?) -> Unit,
) {
    WidgetTemplateConfigScreenContent(
        business = business,
        sourceFilePath = sourceFilePath,
        cardStorageKey = cardStorageKey,
        currentConfigJson = currentConfigJson,
        onBack = onBack,
        onSave = onSave,
    )
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun RearWallpaperTemplateConfigScreen(
    wallpaper: RearWallpaperInfo,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val titleText = stringResource(R.string.rear_wallpaper_template_title)
    val loadingText = stringResource(R.string.rear_wallpaper_template_loading)
    val unavailableText = stringResource(R.string.rear_wallpaper_template_unavailable)

    WidgetTemplateConfigScreenContent(
        business = "",
        sourceFilePath = "wallpaper:${wallpaper.wallpaperId}",
        cardStorageKey = "wallpaper_${wallpaper.wallpaperId}",
        currentConfigJson = null,
        titleText = titleText,
        loadingText = loadingText,
        unavailableText = unavailableText,
        stateResolver = { resolverContext, _, _, currentConfigJson ->
            RearWallpaperRepository.resolveTemplateConfigState(
                context = resolverContext,
                wallpaperId = wallpaper.wallpaperId,
                currentOneConfigJson = currentConfigJson,
            )
        },
        configNormalizer = { normalizerContext, _, _, encoded ->
            RearWallpaperRepository.resolveTemplateConfigState(
                context = normalizerContext,
                wallpaperId = wallpaper.wallpaperId,
                currentOneConfigJson = encoded,
            )?.oneConfigJson
        },
        onBack = onBack,
        onSave = { normalizedJson ->
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    RearWallpaperRepository.saveTemplateConfig(
                        context = context,
                        wallpaperId = wallpaper.wallpaperId,
                        oneConfigJson = normalizedJson,
                    )
                }
                if (result.success) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.rear_wallpaper_template_saved),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onSaved()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.rear_wallpaper_template_save_failed,
                            result.error ?: "unknown",
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
    )
}
