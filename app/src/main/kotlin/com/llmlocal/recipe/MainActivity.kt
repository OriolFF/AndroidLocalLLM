package com.llmlocal.recipe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.llmlocal.core.designsystem.component.AppScaffold
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.feature.recipe.RecipeRoute

/**
 * Single Activity for the app. Hosts the recipe feature.
 *
 * Uses [enableEdgeToEdge] to render behind the system bars and lets
 * [AppScaffold] handle window insets for the rest of the UI.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                RecipeAppRoot()
            }
        }
    }
}

@Composable
private fun RecipeAppRoot() {
    val snackbarHostState = remember { SnackbarHostState() }
    AppScaffold(
        title = "Local LLM Recipes",
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding)
                .consumeWindowInsets(WindowInsets.systemBars),
        ) {
            RecipeRoute(snackbarHostState = snackbarHostState)
        }
    }
}
