package hk.uwu.reareye.ui.screen

import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import hk.uwu.reareye.R
import hk.uwu.reareye.generated.AppProperties
import hk.uwu.reareye.ui.components.card.SuperCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private data class CreditEntry(
    val titleRes: Int,
    val summaryRes: Int,
    val url: String,
)

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val versionText = rememberVersionText()

    val entries = listOf(
        CreditEntry(
            titleRes = R.string.credits_github_title,
            summaryRes = R.string.credits_github_desc,
            url = "https://github.com/killerprojecte/REAREye",
        ),
        CreditEntry(
            titleRes = R.string.credits_afdian_title,
            summaryRes = R.string.credits_afdian_desc,
            url = "https://ifdian.net/a/rgbmc",
        ),
        CreditEntry(
            titleRes = R.string.credits_qq_title,
            summaryRes = R.string.credits_qq_desc,
            url = "https://qm.qq.com/q/cg2MU3kw6W"
        ),
        CreditEntry(
            titleRes = R.string.credits_coolapk_title,
            summaryRes = R.string.credits_coolapk_desc,
            url = "https://www.coolapk.com/u/7190992"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.about_navigation),
                scrollBehavior = scrollBehavior,
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = paddingValues,
            overscrollEffect = null,
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                    pressFeedbackType = PressFeedbackType.Sink,
                    showIndication = true
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(modifier = Modifier.size(52.dp))
                        Spacer(modifier = Modifier.size(12.dp))
                        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MiuixTheme.textStyles.title3,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = versionText,
                                style = MiuixTheme.textStyles.body2,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }

            itemsIndexed(entries) { _, entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    SuperCard(
                        title = stringResource(entry.titleRes),
                        summary = stringResource(entry.summaryRes),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, entry.url.toUri()))
                        },
                        endActions = {
                            Icon(
                                imageVector = MiuixIcons.Link,
                                tint = colorScheme.onSurface,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberVersionText(): String {
    return "${AppProperties.PROJECT_APP_VERSION_NAME}-${AppProperties.GIT_HASH}-r${AppProperties.BUILD_NUMBER}-${AppProperties.BUILD_CHANNEL}"
}

@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(context.packageName)
                val bitmap = if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val bmp = createBitmap(
                        drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
                        drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,
                    )
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
                imageBitmap = bitmap.asImageBitmap()
            }
        }
    }

    if (imageBitmap != null) {
        Image(bitmap = imageBitmap!!, contentDescription = null, modifier = modifier)
    } else {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariantSummary,
            modifier = modifier,
        )
    }
}
