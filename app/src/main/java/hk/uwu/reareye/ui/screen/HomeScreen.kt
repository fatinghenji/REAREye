package hk.uwu.reareye.ui.screen

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.highcapable.yukihookapi.YukiHookAPI
import hk.uwu.reareye.R
import hk.uwu.reareye.generated.AppProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun HomeScreen() {
    val isActivated = YukiHookAPI.Status.isModuleActive

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var latestCommitHash by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isCheckingUpdate = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val branch = AppProperties.GIT_BRANCH.split("/")
                val request = Request.Builder()
                    .url("https://api.github.com/repos/${branch[0]}/${branch[1]}/commits/${branch[2]}")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body.string()
                    val json = JSONObject(body ?: "")
                    val sha = json.optString("sha", "")
                    val shortHash = sha.take(7)
                    if (shortHash != AppProperties.GIT_HASH) {
                        withContext(Dispatchers.Main) {
                            latestCommitHash = shortHash
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore network errors
            } finally {
                withContext(Dispatchers.Main) {
                    isCheckingUpdate = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Box(contentAlignment = Alignment.CenterEnd) {
                TopAppBar(title = stringResource(R.string.app_name))
                Image(
                    painter = painterResource(id = R.drawable.ic_github),
                    contentDescription = "GitHub",
                    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onBackground),
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp)
                        .clickable {
                            val intent =
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/killerprojecte/REAREye".toUri()
                                )
                            context.startActivity(intent)
                        }
                )
            }
        }
    ) { paddingValues ->
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 4 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Update reminder card
                    AnimatedVisibility(
                        visible = latestCommitHash != null,
                        enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 4 }
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = PaddingValues(2.dp),
                            colors = CardDefaults.defaultColors(
                                MiuixTheme.colorScheme.error,
                                MiuixTheme.colorScheme.errorContainer
                            ),
                            showIndication = true,
                            pressFeedbackType = PressFeedbackType.Sink
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = PaddingValues(16.dp),
                                cornerRadius = 15.dp,
                                colors = CardDefaults.defaultColors(
                                    MiuixTheme.colorScheme.errorContainer,
                                    MiuixTheme.colorScheme.onErrorContainer
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.version_card_title),
                                    style = MiuixTheme.textStyles.title3,
                                    color = MiuixTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(
                                        R.string.version_card_current,
                                        AppProperties.GIT_HASH
                                    ),
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(
                                        R.string.version_card_latest,
                                        latestCommitHash ?: "ERROR"
                                    ),
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(16.dp),
                        colors = if (isActivated) CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.primaryVariant
                        ) else CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.error
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.status_card),
                            style = MiuixTheme.textStyles.title3,
                            color = if (isActivated) MiuixTheme.colorScheme.onPrimaryVariant else MiuixTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(
                                R.string.module_version,
                                "${AppProperties.PROJECT_APP_VERSION_NAME}-${AppProperties.GIT_HASH}-r${AppProperties.BUILD_NUMBER}-${AppProperties.BUILD_CHANNEL}"
                            ),
                            style = MiuixTheme.textStyles.subtitle,
                            color = if (isActivated) MiuixTheme.colorScheme.onPrimaryVariant else MiuixTheme.colorScheme.onError
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isActivated) stringResource(R.string.module_is_activated) else stringResource(
                                R.string.module_not_activated
                            ),
                            style = MiuixTheme.textStyles.subtitle,
                            color = if (isActivated) MiuixTheme.colorScheme.onPrimaryVariant else MiuixTheme.colorScheme.onError
                        )
                    }
                }
            }
        }
    }
}
