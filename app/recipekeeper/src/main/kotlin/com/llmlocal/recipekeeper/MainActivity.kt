package com.llmlocal.recipekeeper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MenuBook
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
import com.llmlocal.core.designsystem.component.EmptyState
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.feature.modelmanagement.ModelManagementRoute
import com.llmlocal.recipekeeper.config.RecipeKeeperModelManagementLabels
import com.llmlocal.recipekeeper.designsystem.RecipeKeeperTokens

/**
 * Single Activity for Recipe Keeper.
 *
 * WS0 ships two destinations under one [AppScaffold]:
 *
 *  - `library`         — start; placeholder empty state until WS5b lands
 *                        the real library grid.
 *  - `modelManagement` — discover / download / select / remove models.
 *
 * WS5b adds `browser?url={url}`, `extract/{candidateId}` and
 * `recipe/{recipeId}` (which own their scaffolds) and the back-stack rules
 * from the UX spec §1.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme(tokens = RecipeKeeperTokens) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(WindowInsets.systemBars),
        ) {
            NavHost(
                navController = navController,
                startDestination = Route.Library.path,
            ) {
                composable(Route.Library.path) {
                    // Placeholder until WS5b lands LibraryScreen.
                    EmptyState(
                        icon = Icons.Outlined.MenuBook,
                        title = "No recipes yet",
                        message = "WS5b",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                composable(Route.ModelManagement.path) {
                    ModelManagementRoute(
                        snackbarHostState = snackbarHostState,
                        labels = RecipeKeeperModelManagementLabels,
                    )
                }
            }
        }
    }
}

/** Route declarations for the app's NavHost. */
private sealed class Route(val path: String) {
    data object Library : Route("library")
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
    else -> "Recipe Keeper"
}
