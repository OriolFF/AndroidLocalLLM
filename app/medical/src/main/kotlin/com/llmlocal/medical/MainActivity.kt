package com.llmlocal.medical

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
import androidx.compose.ui.res.stringResource
import com.llmlocal.medical.R
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.llmlocal.core.designsystem.component.AppScaffold
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.feature.modelmanagement.presentation.ModelManagementRoute
import com.llmlocal.medical.config.rememberMedicalModelManagementLabels
import com.llmlocal.medical.designsystem.MedicalTokens
import com.llmlocal.medical.medical.presentation.MedicalScreen

/**
 * Single Activity for the medical app. Hosts a `NavHost` with two
 * destinations:
 *
 *  - `medical` (start destination) — the symptom / advice flow.
 *  - `modelManagement` — discover / download / select / remove models.
 *
 * The top app bar carries:
 *  - A **back arrow** whenever the back stack is non-empty.
 *  - A `Psychology` icon in the trailing slot on the root destination
 *    only — it navigates forward to model management.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme(tokens = MedicalTokens) {
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
                        contentDescription = stringResource(R.string.medical_nav_back),
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
                        contentDescription = stringResource(R.string.medical_nav_manage_models),
                    )
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(WindowInsets.systemBars),
        ) {
            NavHost(
                navController = navController,
                startDestination = Route.Medical.path,
            ) {
                composable(Route.Medical.path) {
                    MedicalScreen(
                        onNavigateToModelManagement = { navController.navigateToModelManagement() },
                    )
                }
                composable(Route.ModelManagement.path) {
                    ModelManagementRoute(
                        snackbarHostState = snackbarHostState,
                        labels = rememberMedicalModelManagementLabels(),
                    )
                }
            }
        }
    }
}

/** Route declarations for the app's NavHost. */
private sealed class Route(val path: String) {
    data object Medical : Route("medical")
    data object ModelManagement : Route("modelManagement")
}

/**
 * Convenience: navigate to the model management destination, popping
 * back to the start destination first so the back-stack doesn't grow
 * unbounded.
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

@Composable
private fun screenTitle(route: String?): String = when (route) {
    Route.ModelManagement.path -> stringResource(R.string.medical_nav_title_models)
    else -> stringResource(R.string.app_name)
}