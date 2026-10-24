package com.llmlocal.core.designsystem.color

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The [ImageAccent] of the picture at [path], or `null` while it is being
 * computed, when [path] is `null`, or when the file cannot be decoded.
 *
 * Decoding runs once per path on [Dispatchers.Default] through
 * [ImageAccentLoader]; a cached accent is returned synchronously on the first
 * composition so revisiting a screen never flashes an uncoloured frame.
 */
@Composable
fun rememberImageAccent(path: String?): ImageAccent? {
    if (path == null) return null
    val file = remember(path) { File(path) }
    val accent: State<ImageAccent?> = produceState(initialValue = ImageAccentLoader.peek(file), file) {
        if (value == null) {
            value = withContext(Dispatchers.Default) { ImageAccentLoader.load(file) }
        }
    }
    val current by accent
    return current
}
