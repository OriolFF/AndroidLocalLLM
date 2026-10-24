package com.llmlocal.recipekeeper.app

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.llmlocal.feature.modelmanagement.presentation.ModelManagementRoute
import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.ClipSink
import com.llmlocal.feature.webclipper.domain.input.ClipInputInbox
import com.llmlocal.feature.webclipper.domain.input.PendingClipInput
import com.llmlocal.feature.webclipper.domain.IMAGES_CANDIDATE_ID
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.presentation.WebClipperBrowserRoute
import com.llmlocal.feature.webclipper.presentation.WebClipperExtractionRoute
import com.llmlocal.feature.webclipper.presentation.images.WebClipperImagesRoute
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperDraftParser
import com.llmlocal.recipekeeper.clip.presentation.ClipDraftCard
import com.llmlocal.recipekeeper.clip.presentation.rememberRecipeKeeperClipperLabels
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.recipes.domain.usecase.DeleteRecipeWithUndoUseCase
import com.llmlocal.recipekeeper.recipes.presentation.rememberRecipeKeeperLibraryLabels
import com.llmlocal.recipekeeper.recipes.presentation.detail.RecipeDetailRoute
import com.llmlocal.recipekeeper.recipes.presentation.editor.EditorMode
import com.llmlocal.recipekeeper.recipes.presentation.editor.RecipeEditorRoute
import com.llmlocal.recipekeeper.recipes.presentation.editor.rememberRecipeEditorLabels
import com.llmlocal.recipekeeper.recipes.presentation.library.LibraryRoute
import com.llmlocal.recipekeeper.recipes.presentation.library.showRecipeDeleted
import com.llmlocal.recipekeeper.settings.presentation.SettingsRoute
import com.llmlocal.recipekeeper.settings.presentation.rememberSettingsLabels
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Incremental parser feeding the streaming draft card; stateless, so one instance is enough. */
private val draftParser = RecipeKeeperDraftParser()

/**
 * Navigation graph for Recipe Keeper, hosted by [MainActivity]. The graph from UX spec §1:
 *
 * | Destination      | Route                    | Chrome                     |
 * |------------------|--------------------------|----------------------------|
 * | Library (start)  | `library`                | `AppScaffold` (in route)   |
 * | Browser          | `browser?url={url}`      | own scaffold (feature)     |
 * | Images clip      | `clip/images`            | own scaffold (feature)     |
 * | Extraction       | `extract/{candidateId}`  | own scaffold (feature)     |
 * | Recipe detail    | `recipe/{recipeId}`      | own scaffold (collapsing)  |
 * | Editor (create)  | `recipe/new`             | own scaffold (editor)      |
 * | Editor (edit)    | `recipe/{recipeId}/edit` | own scaffold (editor)      |
 * | Settings         | `settings`               | `AppScaffold` (in route)   |
 * | Model management | `modelManagement`        | `AppScaffold`              |
 *
 * Back-stack rules: extraction → detail pops extraction with
 * `popUpTo(browser)` so back from a fresh clip returns to the page;
 * browser re-entry (library FAB, "Open original") is `launchSingleTop`;
 * model management is pushed from wherever the user was (library banner,
 * browser FAB, extraction error, settings) so back returns there; settings
 * is pushed from the library gear. One [SnackbarHostState] is shared by every scaffold.
 */
@Composable
fun RecipeKeeperNavHost() {
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val labels = rememberRecipeKeeperLibraryLabels()
    val editorLabels = rememberRecipeEditorLabels()
    val deleteWithUndo = koinInject<DeleteRecipeWithUndoUseCase>()

    // Create lands on the new recipe's detail (the editor leaves the back stack, so back
    // returns to the library); edit pops to wherever it was opened from. The edit
    // confirmation is shown in the root scope so it outlives the popped editor.
    fun onEditorSaved(nav: NavHostController, id: String, mode: EditorMode) {
        when (mode) {
            EditorMode.CREATE -> nav.navigate(Route.Recipe.build(id)) {
                popUpTo(Route.Library.pattern)
            }
            EditorMode.EDIT -> {
                nav.popBackStack()
                scope.launch { snackbarHostState.showSnackbar(labels.changesSaved) }
            }
        }
    }

    // Share arrivals (pictures, links) land in the inbox; this host is the only consumer.
    val inbox = koinInject<ClipInputInbox>()
    val clipSession = koinInject<ClipSession>()
    val clipperLabels = rememberRecipeKeeperClipperLabels()
    val pending by inbox.pending.collectAsStateWithLifecycle()
    var sharedImageUris by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        val input = inbox.consume() ?: return@LaunchedEffect
        if (clipSession.activeExtraction.value != null) {
            snackbarHostState.showSnackbar(clipperLabels.clipAlreadyRunning)
            return@LaunchedEffect
        }
        when (input) {
            is PendingClipInput.Images -> {
                sharedImageUris = input.uris
                navController.navigate(Route.ClipImages.pattern) {
                    launchSingleTop = true
                    // A second share while the screen is open starts it afresh with the new pictures.
                    popUpTo(Route.ClipImages.pattern) { inclusive = true }
                }
            }
            is PendingClipInput.Url -> navController.navigateToBrowser(input.url)
        }
    }

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
                onOpenSettings = { navController.navigateToSettings() },
                onOpenEditor = { id -> navController.navigateToEditor(id) },
                onOpenImagesClip = { navController.navigateToImagesClip() },
            )
        }

        // Prepare/review screen of the images source; unreachable while IMAGES_SOURCE is off.
        composable(Route.ClipImages.pattern) {
            WebClipperImagesRoute(
                config = koinInject<WebClipConfig<ClippedRecipe>>(),
                labels = rememberRecipeKeeperClipperLabels(),
                pendingUris = sharedImageUris,
                onNavigateToExtraction = { candidateId ->
                    sharedImageUris = null
                    navController.navigate(Route.Extract.build(candidateId)) {
                        popUpTo(Route.ClipImages.pattern) { inclusive = true }
                    }
                },
                onClosed = {
                    sharedImageUris = null
                    navController.popBackStack()
                },
                onNavigateToModelManagement = { navController.navigateToModelManagement() },
                snackbarHostState = snackbarHostState,
                onShowSnackbar = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
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
                labels = rememberRecipeKeeperClipperLabels(),
                initialUrl = entry.arguments?.getString(Route.Browser.ARG_URL),
                onNavigateToModelManagement = { navController.navigateToModelManagement() },
                onNavigateToExtraction = { candidateId -> navController.navigate(Route.Extract.build(candidateId)) },
                onBackToLibrary = { navController.popBackStack() },
                snackbarHostState = snackbarHostState,
                onShowSnackbar = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
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
                labels = rememberRecipeKeeperClipperLabels(),
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
                        // An images clip has no browser behind it: back from the saved recipe lands on the library.
                        popUpTo(if (candidateId == IMAGES_CANDIDATE_ID) Route.Library.pattern else Route.Browser.pattern)
                    }
                },
                onCancelled = { navController.popBackStack() },
                onNavigateToModelManagement = { navController.navigateToModelManagement() },
                onPickDifferent = if (candidateId == IMAGES_CANDIDATE_ID) {
                    { navController.navigateToPickDifferentImages() }
                } else {
                    null
                },
                // Without the shared host "Clip cancelled" / "Saved to your
                // recipes" were silently dropped (WS10 §Scenario 5).
                snackbarHostState = snackbarHostState,
                // Root scope: the snackbar must outlive this popped destination.
                onShowSnackbar = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
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
                onEdit = { id -> navController.navigateToEditor(id) },
                onDeleted = { id ->
                    navController.popBackStack()
                    // Launched in the root scope so the snackbar outlives the popped screen.
                    scope.launch {
                        snackbarHostState.showRecipeDeleted(
                            labels = labels,
                            onUndo = {
                                if (!deleteWithUndo.undo(id)) {
                                    scope.launch { snackbarHostState.showSnackbar(labels.undoDeleteFailed) }
                                }
                            },
                            onDismissed = { scope.launch { deleteWithUndo.commit(id) } },
                        )
                    }
                },
            )
        }

        composable(Route.NewRecipe.pattern) {
            RecipeEditorRoute(
                recipeId = null,
                labels = editorLabels,
                snackbarHostState = snackbarHostState,
                onSaved = { id, mode -> onEditorSaved(navController, id, mode) },
                onClosed = { navController.popBackStack() },
            )
        }

        composable(
            route = Route.EditRecipe.pattern,
            arguments = listOf(navArgument(Route.EditRecipe.ARG_ID) { type = NavType.StringType }),
        ) { entry ->
            RecipeEditorRoute(
                recipeId = entry.arguments?.getString(Route.EditRecipe.ARG_ID).orEmpty(),
                labels = editorLabels,
                snackbarHostState = snackbarHostState,
                onSaved = { id, mode -> onEditorSaved(navController, id, mode) },
                onClosed = { navController.popBackStack() },
            )
        }

        composable(Route.Settings.pattern) {
            SettingsRoute(
                labels = rememberSettingsLabels(),
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onOpenModelManagement = { navController.navigateToModelManagement() },
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
                    labels = rememberRecipeKeeperModelManagementLabels(),
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

    data object ClipImages : Route("clip/images")

    data object NewRecipe : Route("recipe/new")

    data object EditRecipe : Route("recipe/{recipeId}/edit") {
        const val ARG_ID = "recipeId"
        fun build(id: String): String = "recipe/${Uri.encode(id)}/edit"
    }

    data object Settings : Route("settings")

    data object ModelManagement : Route("modelManagement")
}

/** Browser entry: `launchSingleTop` so re-opening from Detail or the FAB never stacks browsers. */
private fun NavHostController.navigateToBrowser(url: String?) {
    navigate(Route.Browser.build(url)) {
        launchSingleTop = true
    }
}

/** Editor entry: `null` creates a recipe, an id edits it; `launchSingleTop` so a double tap never stacks editors. */
private fun NavHostController.navigateToEditor(id: String?) {
    navigate(if (id == null) Route.NewRecipe.pattern else Route.EditRecipe.build(id)) {
        launchSingleTop = true
    }
}

/** Images source entry; `launchSingleTop` so a double tap never stacks two. */
private fun NavHostController.navigateToImagesClip() {
    navigate(Route.ClipImages.pattern) {
        launchSingleTop = true
    }
}

/**
 * "Pick different pictures" on the extraction screen: back to the prepare/review screen, which reads the
 * prepared images from the session.
 */
private fun NavHostController.navigateToPickDifferentImages() {
    navigate(Route.ClipImages.pattern) {
        launchSingleTop = true
        popUpTo(Route.Extract.pattern) { inclusive = true }
    }
}

/** Pushed from the library gear; `launchSingleTop` so a double tap never stacks two. */
private fun NavHostController.navigateToSettings() {
    navigate(Route.Settings.pattern) {
        launchSingleTop = true
    }
}

/** Pushed from wherever the user is (library, browser FAB, extraction error, settings) so back returns there. */
private fun NavHostController.navigateToModelManagement() {
    navigate(Route.ModelManagement.pattern) {
        launchSingleTop = true
    }
}
