package com.llmlocal.recipekeeper.settings.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.SectionHeader
import com.llmlocal.recipekeeper.settings.presentation.mvi.SettingsIntent
import com.llmlocal.recipekeeper.settings.presentation.mvi.SettingsState
import com.llmlocal.recipekeeper.settings.presentation.mvi.StartPageOption

/**
 * Stateless settings screen: a "Browser" card with the start-page radio
 * group (Google / blank / custom address, the field unfolding under the
 * custom row) and a "Model" card whose single row opens model management.
 * Rows are 56dp tall and selectable as a whole for touch and TalkBack.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    labels: SettingsLabels,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(label = labels.browserSection, icon = Icons.Outlined.Language)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(labels.startPageTitle, style = MaterialTheme.typography.titleMedium)
                        Text(
                            labels.startPageSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!state.isLoading) {
                        StartPageOptions(state = state, labels = labels, onIntent = onIntent)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(label = labels.modelsSection, icon = Icons.Outlined.Psychology)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(onClick = { onIntent(SettingsIntent.OpenModelManagement) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(labels.manageModels, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            labels.manageModelsSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StartPageOptions(
    state: SettingsState,
    labels: SettingsLabels,
    onIntent: (SettingsIntent) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val customFieldFocus = remember { FocusRequester() }
    val customSelected = state.startPage == StartPageOption.CUSTOM
    // Picking "Custom address" with nothing saved yet puts the caret in the field.
    LaunchedEffect(customSelected) {
        if (customSelected && state.customUrl.isBlank()) customFieldFocus.requestFocus()
    }
    Column(modifier = Modifier.selectableGroup()) {
        StartPageRow(
            title = labels.startPageGoogle,
            subtitle = labels.startPageGoogleSubtitle,
            selected = state.startPage == StartPageOption.GOOGLE,
            onSelect = { onIntent(SettingsIntent.SelectStartPage(StartPageOption.GOOGLE)) },
        )
        StartPageRow(
            title = labels.startPageBlank,
            subtitle = labels.startPageBlankSubtitle,
            selected = state.startPage == StartPageOption.BLANK,
            onSelect = { onIntent(SettingsIntent.SelectStartPage(StartPageOption.BLANK)) },
        )
        StartPageRow(
            title = labels.startPageCustom,
            subtitle = labels.startPageCustomSubtitle,
            selected = state.startPage == StartPageOption.CUSTOM,
            onSelect = { onIntent(SettingsIntent.SelectStartPage(StartPageOption.CUSTOM)) },
        )
        AnimatedVisibility(visible = customSelected) {
            Column(modifier = Modifier.padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
                OutlinedTextField(
                    value = state.customUrl,
                    onValueChange = { onIntent(SettingsIntent.UpdateCustomUrl(it)) },
                    modifier = Modifier.fillMaxWidth().focusRequester(customFieldFocus),
                    label = { Text(labels.customUrlLabel) },
                    placeholder = { Text(labels.customUrlHint) },
                    singleLine = true,
                    isError = state.customUrlError,
                    supportingText = {
                        Text(if (state.customUrlError) labels.customUrlInvalid else labels.customUrlHelp)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onIntent(SettingsIntent.CommitCustomUrl)
                            focusManager.clearFocus()
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun StartPageRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
