package com.llmlocal.recipekeeper

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.llmlocal.core.designsystem.component.AppScaffold
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.feature.modelmanagement.ModelManagementRoute
import com.llmlocal.feature.webclipper.ClipSink
import com.llmlocal.feature.webclipper.WebClipConfig
import com.llmlocal.feature.webclipper.WebClipperBrowserRoute
import com.llmlocal.feature.webclipper.WebClipperExtractionRoute
import com.llmlocal.recipekeeper.config.DefaultRecipeKeeperLibraryLabels
import com.llmlocal.recipekeeper.config.RecipeKeeperClipperLabels
import com.llmlocal.recipekeeper.config.RecipeKeeperModelManagementLabels
import com.llmlocal.recipekeeper.designsystem.RecipeKeeperTokens
import com.llmlocal.recipekeeper.detail.RecipeDetailRoute
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.library.LibraryRoute
import com.llmlocal.recipekeeper.library.PendingRecipeDeletes
import com.llmlocal.recipekeeper.library.showRecipeDeleted
import com.llmlocal.recipekeeper.output.ClipDraftCard
import com.llmlocal.recipekeeper.parser.RecipeKeeperDraftParser
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Single Activity for Recipe Keeper. Hosts the `NavHost` from UX spec §1:
 *
 * | Destination      | Route                    | Chrome                     |
 * |------------------|--------------------------|----------------------------|
 * | Library (start)  | `library`                | `AppScaffold` (in route)   |
 * | Browser          | `browser?url={url}`      | own scaffold (feature)     |
 * | Extraction       | `extract/{candidateId}`  | own scaffold (feature)     |
 * | Recipe detail    | `recipe/{recipeId}`      | own scaffold (collapsing)  |
 * | Model management | `modelManagement`        | `AppScaffold`              |
 *
 * Back-stack rules: extraction → detail pops extraction with
 * `popUpTo(browser)` so back from a fresh clip returns to the page;
 * browser re-entry (library FAB, "Open original") is `launchSingleTop`;
 * model management is pushed from wherever the user was so back returns
 * there. One [SnackbarHostState] is shared by every scaffold.
 */
/** Incremental parser feeding the streaming draft card; stateless, so one instance is enough. */
private val draftParser = RecipeKeeperDraftParser()

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
    val scope = rememberCoroutineScope()
    val labels = DefaultRecipeKeeperLibraryLabels
    val pendingDeletes = koinInject<PendingRecipeDeletes>()

    NavHost(
        navController = navController,
        startDestination = Route.Library.pattern,
    ) {
        composable(Route.Library.pattern) {
            LibraryRoute(
                labels = labels,
                snackbarHostState = snackbarHostState,
                onOpenRecipe = { id -> navController.navigate(Route.Recipe.build(id)) },
                onOpenBrowser = { url -> navController.navigateToBrowser(url) },
                onOpenModelManagement = { navController.navigateToModelManagement() },
            )
        }

        composable(
            route = Route.Browser.pattern,
            arguments = listOf(
                navArgument(Route.Browser.ARG_URL) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            WebClipperBrowserRoute(
                config = koinInject<WebClipConfig<ClippedRecipe>>(),
                sink = koinInject<ClipSink<ClippedRecipe>>(),
                labels = RecipeKeeperClipperLabels,
                initialUrl = entry.arguments?.getString(Route.Browser.ARG_URL),
                onNavigateToModelManagement = { navController.navigateToModelManagement() },
                onNavigateToExtraction = { candidateId -> navController.navigate(Route.Extract.build(candidateId)) },
                onBackToLibrary = { navController.popBackStack() },
                snackbarHostState = snackbarHostState,
            )
        }

        composable(
            route = Route.Extract.pattern,
            arguments = listOf(navArgument(Route.Extract.ARG_CANDIDATE) { type = NavType.StringType }),
        ) { entry ->
            val candidateId = entry.arguments?.getString(Route.Extract.ARG_CANDIDATE).orEmpty()
            WebClipperExtractionRoute(
                candidateId = candidateId,
                config = koinInject<WebClipConfig<ClippedRecipe>>(),
                sink = koinInject<ClipSink<ClippedRecipe>>(),
                labels = RecipeKeeperClipperLabels,
                renderDraft = { streamedText, isRunning ->
                    ClipDraftCard(
                        streamedText = streamedText,
                        isRunning = isRunning,
                        parseDraft = draftParser::parse,
                        labels = labels,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                },
                onSaved = { id ->
                    navController.navigate(Route.Recipe.build(id)) {
                        popUpTo(Route.Browser.pattern)
                    }
                },
                onCancelled = { navController.popBackStack() },
                onNavigateToModelManagement = { navController.navigateToModelManagement() },
            )
        }

        composable(
            route = Route.Recipe.pattern,
            arguments = listOf(navArgument(Route.Recipe.ARG_ID) { type = NavType.StringType }),
        ) { entry ->
            val recipeId = entry.arguments?.getString(Route.Recipe.ARG_ID).orEmpty()
            RecipeDetailRoute(
                recipeId = recipeId,
                labels = labels,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onOpenBrowser = { url -> navController.navigateToBrowser(url) },
                onDeleted = { id ->
                    navController.popBackStack()
                    // Launched in the root scope so the snackbar outlives the popped screen.
                    scope.launch {
                        snackbarHostState.showRecipeDeleted(
                            labels = labels,
                            onUndo = {
                                if (!pendingDeletes.undo(id)) {
                                    scope.launch { snackbarHostState.showSnackbar(labels.undoDeleteFailed) }
                                }
                            },
                            onDismissed = { scope.launch { pendingDeletes.commit(id) } },
                        )
                    }
                },
            )
        }

        composable(Route.ModelManagement.pattern) {
            AppScaffold(
                title = labels.modelsTitle,
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = labels.back)
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { padding ->
                ModelManagementRoute(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    snackbarHostState = snackbarHostState,
                    labels = RecipeKeeperModelManagementLabels,
                )
            }
        }
    }
}

/** Route patterns and builders for the app's NavHost. */
private sealed class Route(val pattern: String) {
    data object Library : Route("library")

    data object Browser : Route("browser?url={url}") {
        const val ARG_URL = "url"
        fun build(url: String?): String =
            if (url.isNullOrBlank()) "browser" else "browser?url=${Uri.encode(url)}"
    }

    data object Extract : Route("extract/{candidateId}") {
        const val ARG_CANDIDATE = "candidateId"
        fun build(candidateId: String): String = "extract/${Uri.encode(candidateId)}"
    }

    data object Recipe : Route("recipe/{recipeId}") {
        const val ARG_ID = "recipeId"
        fun build(recipeId: String): String = "recipe/${Uri.encode(recipeId)}"
    }

    data object ModelManagement : Route("modelManagement")
}

/** Browser entry: `launchSingleTop` so re-opening from Detail or the FAB never stacks browsers. */
private fun NavHostController.navigateToBrowser(url: String?) {
    navigate(Route.Browser.build(url)) {
        launchSingleTop = true
    }
}

/** Pushed from wherever the user is (library, browser FAB, extraction error) so back returns there. */
private fun NavHostController.navigateToModelManagement() {
    navigate(Route.ModelManagement.pattern) {
        launchSingleTop = true
    }
}
