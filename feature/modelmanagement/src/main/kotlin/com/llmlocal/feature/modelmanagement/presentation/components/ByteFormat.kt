package com.llmlocal.feature.modelmanagement.presentation.components

/** Human-readable byte count: `2.58 GB`, `420.0 MB`, `12 KB`, `7 B`. */
/** `4096` → `4k`, `1280` → `1280`; for the context-window spec. */
@androidx.compose.runtime.Composable
internal fun formatTokens(tokens: Int): String =
    if (tokens >= 1024 && tokens % 1024 == 0) {
        androidx.compose.ui.res.stringResource(com.llmlocal.feature.modelmanagement.R.string.modelmanagement_tokens_k, tokens / 1024)
    } else {
        androidx.compose.ui.res.stringResource(com.llmlocal.feature.modelmanagement.R.string.modelmanagement_tokens, tokens)
    }

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1L * 1024 * 1024 * 1024 ->
        "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1L * 1024 * 1024 ->
        "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1L * 1024 ->
        "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
