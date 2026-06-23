package com.llmlocal.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * App-wide scaffold. Sets sensible defaults for the content window insets so
 * feature screens can stay edge-to-edge without handling insets themselves.
 *
 * The scaffold forwards the standard Material 3 `TopAppBar` slots to its
 * caller:
 *
 *  - [navigationIcon]  — leading slot. Typically an up-arrow
 *    ([androidx.compose.material.icons.automirrored.filled.ArrowBack]) shown
 *    only when the back stack is non-empty. Pass an empty composable on the
 *    root destination.
 *  - [actions]         — trailing slot. The model-management entry point
 *    adds a `Psychology` icon here, hidden on the model-management
 *    destination itself.
 *
 * Hosts should not handle window-inset math themselves — the [Scaffold]
 * already wires the system-bar insets to the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = navigationIcon,
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        snackbarHost = snackbarHost,
        content = { padding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .consumeWindowInsets(padding)
                    .consumeWindowInsets(WindowInsets.systemBars),
            ) {
                content(padding)
            }
        },
    )
}