package hk.uwu.reareye.utils.other

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import hk.uwu.reareye.R
import hk.uwu.reareye.ui.components.card.SuperCard
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Serializable
data class AboutLibraries(
    val libraries: List<Library>,
    val licenses: Map<String, License>
)

@Serializable
data class Library(
    val uniqueId: String,
    val artifactVersion: String,
    val name: String,
    val description: String? = null,
    val website: String? = null,
    val developers: List<Developer> = emptyList(),
    val organization: Organization? = null,
    val licenses: List<String> = emptyList()
)

@Serializable
data class Developer(
    val name: String? = null,
    val organisationUrl: String? = null
)

@Serializable
data class Organization(
    val name: String,
    val url: String? = null
)

@Serializable
data class License(
    val name: String,
    val url: String,
    val content: String? = null
)

private val json = Json {
    ignoreUnknownKeys = true
}

fun loadLibraries(context: Context): AboutLibraries {
    val inputStream = context.resources.openRawResource(R.raw.aboutlibraries)

    val jsonString = inputStream
        .bufferedReader()
        .use { it.readText() }

    return json.decodeFromString(jsonString)
}

@Composable
fun LibraryItem(lib: Library) {
    val context = LocalContext.current
    val hasLink = lib.website != null

    Card(modifier = Modifier.fillMaxWidth()) {
        SuperCard(
            title = lib.name,
            summary = buildLibrarySummary(lib),
            endActions = {
                if (hasLink) {
                    Button(
                        colors = ButtonColors(
                            color = Color.Transparent,
                            disabledColor = Color.Transparent,
                            contentColor = Color.Transparent,
                            disabledContentColor = Color.Transparent,
                        ),
                        onClick = {
                            lib.website?.let { targetLink ->
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        targetLink.toUri()
                                    )
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Link,
                            tint = MiuixTheme.colorScheme.onSurface,
                            contentDescription = null,
                        )
                    }
                }
            },
            bottomAction = {
                if (lib.developers.isNotEmpty()) {
                    LibraryDevelopersText(lib.developers)
                }
                if (lib.licenses.isNotEmpty()) {
                    Text(text = "License: " + lib.licenses.joinToString(", "))
                }
            },
        )
    }
}

private fun buildLibrarySummary(lib: Library): String? {
    val parts = buildList {
        add(lib.artifactVersion)
        lib.organization?.name?.let { add(it) }
        lib.description?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

@Composable
private fun LibraryDevelopersText(developers: List<Developer>) {
    val annotated = buildAnnotatedString {
        developers.forEachIndexed { index, developer ->
            val name = developer.name ?: "Unknown"
            val url = developer.organisationUrl

            if (url != null) {
                append("Developer: ")
                val start = length
                append(name)
                addLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            SpanStyle(
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        )
                    ),
                    start = start,
                    end = length,
                )
            } else {
                append("Developer: ")
                append(name)
            }

            if (index < developers.size - 1) {
                append(", ")
            }
        }
    }
    Text(text = annotated)
}