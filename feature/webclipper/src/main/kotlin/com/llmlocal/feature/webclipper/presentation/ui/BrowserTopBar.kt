package com.llmlocal.feature.webclipper.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llmlocal.feature.webclipper.presentation.WebClipperLabels
import com.llmlocal.feature.webclipper.domain.hostOf
import com.llmlocal.feature.webclipper.presentation.mvi.BrowserIntent
import com.llmlocal.feature.webclipper.presentation.mvi.BrowserState

/**
 * Browser top bar: back-to-library arrow, the URL pill, overflow menu and
 * the 2dp page-progress line. Pads for the status bar itself; the screen's
 * `Scaffold` places it edge-to-edge.
 *
 * The pill shows the host (with a lock / globe icon) until tapped; focused
 * it becomes a text field with the full URL selected, `Uri` keyboard and
 * a *Go* action. Input without a scheme that has no dot (or has spaces)
 * is searched via `labels.searchUrlTemplate`.
 */
@Composable
fun BrowserTopBar(
    state: BrowserState,
    labels: WebClipperLabels,
    onIntent: (BrowserIntent) -> Unit,
    onBackToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, tonalElevation = 2.dp) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
            ) {
                IconButton(onClick = onBackToLibrary, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = labels.browserBackToLibrary,
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    UrlPill(
                        state = state,
                        labels = labels,
                        onIntent = onIntent,
                        // widthIn must come first: fillMaxWidth() alone would fix
                        // the min constraint and the cap could never shrink it.
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth(),
                    )
                }
                OverflowMenu(state = state, labels = labels, onIntent = onIntent)
            }
            AnimatedVisibility(visible = state.pageProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { state.pageProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
        }
    }
}

@Composable
private fun UrlPill(
    state: BrowserState,
    labels: WebClipperLabels,
    onIntent: (BrowserIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val secure = state.url.startsWith("https://", ignoreCase = true)
    val hasPage = state.url.isNotBlank()
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceVariant,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
        ) {
            Icon(
                imageVector = if (secure) Icons.Outlined.Lock else Icons.Outlined.Public,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.urlFocused) {
                    UrlTextField(state = state, labels = labels, onIntent = onIntent)
                } else {
                    val display = hostOf(state.url) ?: state.url
                    Text(
                        text = display.ifBlank { labels.urlFieldHint },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (display.isBlank()) scheme.onSurfaceVariant else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClickLabel = labels.urlFieldDescription,
                                role = Role.Button,
                            ) { onIntent(BrowserIntent.UrlFocusChanged(true)) }
                            .semantics { contentDescription = labels.urlFieldDescription },
                    )
                }
            }
            if (hasPage && !state.urlFocused) {
                if (state.isLoading) {
                    IconButton(onClick = { onIntent(BrowserIntent.Stop) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = labels.stopLoading)
                    }
                } else {
                    IconButton(onClick = { onIntent(BrowserIntent.Reload) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Refresh, contentDescription = labels.reloadPage)
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlTextField(
    state: BrowserState,
    labels: WebClipperLabels,
    onIntent: (BrowserIntent) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // The field is only mounted while `urlFocused`, and the ViewModel sets
    // `draftUrl` (= the page URL, fully selected) in the same update that
    // focuses it; afterwards it only echoes UpdateDraftUrl back. So the
    // local value is seeded once and never re-synced: a per-frame sync from
    // `state.draftUrl` lagged one frame behind fast typing and reset the
    // field to a stale draft, leaving a single character (device QA).
    var value by remember {
        mutableStateOf(TextFieldValue(state.draftUrl, selection = TextRange(0, state.draftUrl.length)))
    }
    // `onFocusChanged` also fires with the initial *Inactive* state when the
    // field attaches; without this guard that first event would dismiss the
    // field before `requestFocus()` below ever ran (device QA finding 1).
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // Focus alone does not open the IME when the window itself is still
        // gaining focus (first frame of the screen), so ask explicitly.
        keyboard?.show()
    }

    BasicTextField(
        value = value,
        onValueChange = { next ->
            value = next
            onIntent(BrowserIntent.UpdateDraftUrl(next.text))
        },
        singleLine = true,
        textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge).copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(
            onGo = {
                focusManager.clearFocus()
                onIntent(BrowserIntent.SubmitUrl)
            },
        ),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) {
                    Text(
                        text = labels.urlFieldHint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                inner()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focus ->
                if (focus.isFocused) {
                    hadFocus = true
                } else if (hadFocus && state.urlFocused) {
                    onIntent(BrowserIntent.UrlFocusChanged(false))
                }
            }
            .semantics { contentDescription = labels.urlFieldDescription },
    )
}

@Composable
private fun OverflowMenu(
    state: BrowserState,
    labels: WebClipperLabels,
    onIntent: (BrowserIntent) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val hasPage = state.url.isNotBlank()
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.MoreVert, contentDescription = labels.moreOptions)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(labels.goForward) },
                enabled = state.canGoForward,
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                onClick = {
                    open = false
                    onIntent(BrowserIntent.GoForward)
                },
            )
            DropdownMenuItem(
                text = { Text(labels.sharePage) },
                enabled = hasPage,
                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                onClick = {
                    open = false
                    onIntent(BrowserIntent.SharePage)
                },
            )
            DropdownMenuItem(
                text = { Text(labels.openExternal) },
                enabled = hasPage,
                leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                onClick = {
                    open = false
                    onIntent(BrowserIntent.OpenExternal)
                },
            )
            DropdownMenuItem(
                text = { Text(labels.copyLink) },
                enabled = hasPage,
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = {
                    open = false
                    onIntent(BrowserIntent.CopyLink)
                },
            )
        }
    }
}
