package hk.uwu.reareye.ui.components.config.template

import androidx.compose.runtime.Composable

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
