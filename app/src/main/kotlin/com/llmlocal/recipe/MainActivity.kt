package com.llmlocal.recipe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.llmlocal.core.designsystem.component.AppScaffold
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.feature.modelmanagement.ModelManagementRoute
import com.llmlocal.feature.recipe.RecipeRoute

/**
 * Single Activity for the app. Hosts a `NavHost` with two destinations:
 *
 *  - `recipes`         — default; the recipe-generation screen.
 *  - `modelManagement` — discover / download / select / remove models.
 *
 * The top app bar carries:
 *  - A **back arrow** in the leading slot whenever the back stack is
 *    non-empty (i.e. on the model-management destination). Tapping it
 *    calls `popBackStack()`. The system back gesture / button is also
 *    wired to the same call by `NavHost`.
 *  - A `Psychology` icon in the trailing slot on the root destination
 *    only — it navigates forward to model management. The icon is
 *    hidden on the model-management destination because there's
 *    nothing meaningful to do by tapping it again.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // `previousBackStackEntry` is null only on the start destination, so
    // the back arrow is hidden there and shown on every subsequent
    // destination. The system back gesture is handled by NavHost
    // automatically.
    val canNavigateBack = navController.previousBackStackEntry != null

    AppScaffold(
        title = screenTitle(currentRoute),
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            if (currentRoute != Route.ModelManagement.path) {
                IconButton(onClick = { navController.navigateToModelManagement() }) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = "Manage models",
                    )
                }
            }
        },
    ) { padding ->
        // Apply the Scaffold's inner padding so the screen content does not
        // overlap the top app bar.
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(WindowInsets.systemBars),
        ) {
            NavHost(
                navController = navController,
                startDestination = Route.Recipes.path,
            ) {
                composable(Route.Recipes.path) {
                    RecipeRoute(
                        snackbarHostState = snackbarHostState,
                        onNavigateToModelManagement = { navController.navigateToModelManagement() },
                    )
                }
                composable(Route.ModelManagement.path) {
                    ModelManagementRoute(snackbarHostState = snackbarHostState)
                }
            }
        }
    }
}

/** Route declarations for the app's NavHost. */
private sealed class Route(val path: String) {
    data object Recipes : Route("recipes")
    data object ModelManagement : Route("modelManagement")
}

/**
 * Convenience: navigate to the model management destination, popping back
 * to the start destination first so the back-stack doesn't grow unbounded.
 */
private fun NavHostController.navigateToModelManagement() {
    navigate(Route.ModelManagement.path) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun screenTitle(route: String?): String = when (route) {
    Route.ModelManagement.path -> "Models"
    else -> "Local LLM Recipes"
}