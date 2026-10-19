package com.llmlocal.feature.modelmanagement.presentation.components

/** Human-readable byte count: `2.58 GB`, `420.0 MB`, `12 KB`, `7 B`. */
internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1L * 1024 * 1024 * 1024 ->
        "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1L * 1024 * 1024 ->
        "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1L * 1024 ->
        "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
