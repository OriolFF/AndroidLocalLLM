package com.llmlocal.feature.webclipper.previews

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.core.designsystem.theme.DesignTokens
import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.web.TextSection
import com.llmlocal.feature.webclipper.CandidatePayload
import com.llmlocal.feature.webclipper.CandidateSource
import com.llmlocal.feature.webclipper.ClipCandidate
import com.llmlocal.feature.webclipper.WebClipperLabels
import com.llmlocal.feature.webclipper.mvi.BrowserState
import com.llmlocal.feature.webclipper.mvi.ClipFabState
import com.llmlocal.feature.webclipper.mvi.ExtractionError
import com.llmlocal.feature.webclipper.mvi.ExtractionState
import com.llmlocal.feature.webclipper.mvi.Phase
import com.llmlocal.feature.webclipper.mvi.PhaseStatus
import com.llmlocal.feature.webclipper.mvi.PictureDraft
import com.llmlocal.feature.webclipper.mvi.PictureStatus
import com.llmlocal.core.llm.readiness.ModelReadiness
import com.llmlocal.feature.webclipper.ui.CandidateSheetContent
import com.llmlocal.feature.webclipper.ui.ClipFab
import com.llmlocal.feature.webclipper.ui.NothingDetectedSheetContent
import com.llmlocal.core.designsystem.component.SkeletonLine
import com.llmlocal.core.designsystem.component.StreamingCursor
import com.llmlocal.feature.webclipper.ui.WebClipperBrowserScreen
import com.llmlocal.feature.webclipper.ui.WebClipperExtractionScreen
import com.llmlocal.feature.webclipper.ui.label

/**
 * Android Studio previews for every clip-button state, both sheets, the
 * browser chrome and the extraction screen, in light and dark.
 *
 * The feature has no brand of its own, so previews run under
 * [PreviewTokens] — Material 3 baseline schemes, nothing brand-specific.
 * Apps preview the same screens with their real tokens.
 */

// ---------------------------------------------------------------------------
// Preview-only tokens (M3 baseline; never used at runtime)
// ---------------------------------------------------------------------------

private object PreviewTokens : DesignTokens {
    override val lightColorScheme = lightColorScheme()
    override val darkColorScheme = darkColorScheme()
    override val typography = Typography()
    override val shapes = Shapes()
    override val heroLightGradient: Brush =
        Brush.linearGradient(listOf(lightColorScheme.primaryContainer, lightColorScheme.surface))
    override val heroDarkGradient: Brush =
        Brush.linearGradient(listOf(darkColorScheme.primaryContainer, darkColorScheme.surface))
    override val successColor = lightColorScheme.tertiary
}

@Composable
private fun PreviewTheme(dark: Boolean, content: @Composable () -> Unit) {
    AppTheme(tokens = PreviewTokens, darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

// ---------------------------------------------------------------------------
// Sample data
// ---------------------------------------------------------------------------

private val LABELS = WebClipperLabels()

private fun candidate(id: String, title: String, structured: Boolean, ingredients: Int?, minutes: Int?) = ClipCandidate(
    id = id,
    title = title,
    thumbnailUrl = null,
    snippet = "Bring a large pot of salted water to the boil and cook the pasta until al dente…",
    source = if (structured) CandidateSource.STRUCTURED else CandidateSource.TEXT,
    ingredientCount = ingredients,
    totalMinutes = minutes,
    payload = CandidatePayload.Text(TextSection(title, 2, "…", emptyList(), 0)),
)

private val CANDIDATES = listOf(
    candidate("1", "Tomato & basil pasta", structured = true, ingredients = 8, minutes = 25),
    candidate("2", "Quick garlic bread to serve alongside", structured = false, ingredients = null, minutes = null),
    candidate("3", "Lemon ricotta dessert", structured = true, ingredients = 6, minutes = 40),
)

private val DOWNLOADING = PerModelProgress(
    modelId = "gemma-4-E2B-it",
    state = DownloadState.RUNNING,
    bytesRead = 1_200L * 1024 * 1024,
    totalBytes = 2_580L * 1024 * 1024,
    speedBytesPerSec = 8L * 1024 * 1024,
    etaSeconds = 170,
    updatedAt = 0L,
)

private val FAB_STATES: List<ClipFabState> = listOf(
    ClipFabState.ModelNotReady(),
    ClipFabState.ModelNotReady(DOWNLOADING),
    ClipFabState.PageLoading,
    ClipFabState.Idle,
    ClipFabState.Scanning,
    ClipFabState.Found(1),
    ClipFabState.Found(3),
    ClipFabState.Extracting,
)

private const val STREAMED = """Title: Tomato & basil pasta
Description: A weeknight pasta that leans on ripe tomatoes.
Servings: 4
PrepMinutes: 10
CookMinutes: 15
Ingredients:
- 400 g spaghetti
- 4 ripe tomatoes, chopped
- 2 cloves garlic, sliced
- 3 tbsp olive oil
Steps:
1. Cook the pasta in salted water until al dente.
2. Warm the oil and garlic, add the tom"""

private val RUNNING_STATE = ExtractionState(
    candidate = CANDIDATES[0],
    otherCandidates = 2,
    phases = mapOf(
        Phase.Reading to PhaseStatus.Done,
        Phase.Extracting to PhaseStatus.Active,
        Phase.Pictures to PhaseStatus.Active,
        Phase.Saving to PhaseStatus.Pending,
    ),
    streamedText = STREAMED,
    pictures = listOf(
        PictureDraft("https://example.invalid/1.jpg", status = PictureStatus.Done),
        PictureDraft("https://example.invalid/2.jpg", status = PictureStatus.Downloading),
        PictureDraft("https://example.invalid/3.jpg", status = PictureStatus.Failed, keep = false),
    ),
    isRunning = true,
    sourceHost = "cooking.example",
    pageTitle = "Tomato & basil pasta — Cooking Example",
)

private val SAVED_STATE = RUNNING_STATE.copy(
    phases = mapOf(
        Phase.Reading to PhaseStatus.Done,
        Phase.Extracting to PhaseStatus.Warning,
        Phase.Pictures to PhaseStatus.Warning,
        Phase.Saving to PhaseStatus.Done,
    ),
    isRunning = false,
    truncated = true,
    savedId = "r-1",
)

private val PARSE_FAILED_STATE = RUNNING_STATE.copy(
    phases = mapOf(
        Phase.Reading to PhaseStatus.Done,
        Phase.Extracting to PhaseStatus.Failed,
        Phase.Pictures to PhaseStatus.Pending,
        Phase.Saving to PhaseStatus.Pending,
    ),
    isRunning = false,
    pictures = emptyList(),
    error = ExtractionError.ParseFailed("I'm sorry, I could not find a recipe in this text."),
)

/** Stand-in for the app's `ClipDraftCard`: raw text + skeletons for what is missing. */
@Composable
private fun PreviewDraft(streamedText: String, isRunning: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = streamedText, style = MaterialTheme.typography.bodyMedium)
        if (isRunning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonLine(widthFraction = 0.6f)
                StreamingCursor(modifier = Modifier.padding(start = 4.dp))
            }
            SkeletonLine(widthFraction = 0.8f)
        }
    }
}

// ---------------------------------------------------------------------------
// Clip button
// ---------------------------------------------------------------------------

@Composable
private fun ClipFabGallery() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FAB_STATES.forEach { state ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClipFab(state = state, expanded = true, labels = LABELS, onClick = {})
                ClipFab(state = state, expanded = false, labels = LABELS, onClick = {})
                Text(text = state.label(LABELS), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Preview(name = "ClipFab — light", showBackground = true)
@Composable
private fun ClipFabLightPreview() = PreviewTheme(dark = false) { ClipFabGallery() }

@Preview(name = "ClipFab — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ClipFabDarkPreview() = PreviewTheme(dark = true) { ClipFabGallery() }

// ---------------------------------------------------------------------------
// Sheets
// ---------------------------------------------------------------------------

@Preview(name = "Candidate sheet — light", showBackground = true)
@Composable
private fun CandidateSheetLightPreview() = PreviewTheme(dark = false) {
    CandidateSheetContent(candidates = CANDIDATES, host = "cooking.example", labels = LABELS, onPick = {})
}

@Preview(name = "Candidate sheet — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CandidateSheetDarkPreview() = PreviewTheme(dark = true) {
    CandidateSheetContent(candidates = CANDIDATES, host = "cooking.example", labels = LABELS, onPick = {})
}

@Preview(name = "Nothing detected — light", showBackground = true)
@Composable
private fun NothingDetectedLightPreview() = PreviewTheme(dark = false) {
    NothingDetectedSheetContent(labels = LABELS, onExtractAnyway = {}, onKeepBrowsing = {})
}

@Preview(name = "Nothing detected — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NothingDetectedDarkPreview() = PreviewTheme(dark = true) {
    NothingDetectedSheetContent(labels = LABELS, onExtractAnyway = {}, onKeepBrowsing = {})
}

// ---------------------------------------------------------------------------
// Browser chrome (WebView slot stubbed)
// ---------------------------------------------------------------------------

@Composable
private fun BrowserPreview(fab: ClipFabState, loading: Boolean) {
    val snackbar = remember { SnackbarHostState() }
    WebClipperBrowserScreen(
        state = BrowserState(
            url = "https://www.cooking.example/recipes/tomato-basil-pasta",
            draftUrl = "https://www.cooking.example/recipes/tomato-basil-pasta",
            pageTitle = "Tomato & basil pasta",
            pageProgress = if (loading) 42 else 100,
            isLoading = loading,
            canGoBack = true,
            fab = fab,
            readiness = ModelReadiness.Checking,
        ),
        labels = LABELS,
        onIntent = {},
        onBackToLibrary = {},
        snackbarHostState = snackbar,
        webView = { modifier ->
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                Text("WebView", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Preview(name = "Browser — found, light", showSystemUi = true)
@Composable
private fun BrowserFoundLightPreview() = PreviewTheme(dark = false) { BrowserPreview(ClipFabState.Found(3), loading = false) }

@Preview(name = "Browser — loading, dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BrowserLoadingDarkPreview() = PreviewTheme(dark = true) { BrowserPreview(ClipFabState.PageLoading, loading = true) }

// ---------------------------------------------------------------------------
// Extraction
// ---------------------------------------------------------------------------

@Composable
private fun ExtractionPreview(state: ExtractionState) {
    WebClipperExtractionScreen(
        state = state,
        labels = LABELS,
        elapsedSeconds = 12,
        renderDraft = { text, running -> PreviewDraft(text, running) },
        onIntent = {},
        onNavigateToModelManagement = {},
        onViewSaved = {},
        modifier = Modifier.fillMaxSize(),
    )
}

@Preview(name = "Extraction — running, light", showSystemUi = true)
@Composable
private fun ExtractionRunningLightPreview() = PreviewTheme(dark = false) { ExtractionPreview(RUNNING_STATE) }

@Preview(name = "Extraction — running, dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExtractionRunningDarkPreview() = PreviewTheme(dark = true) { ExtractionPreview(RUNNING_STATE) }

@Preview(name = "Extraction — saved + truncated, light", showSystemUi = true)
@Composable
private fun ExtractionSavedLightPreview() = PreviewTheme(dark = false) { ExtractionPreview(SAVED_STATE) }

@Preview(name = "Extraction — parse failed, dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExtractionParseFailedDarkPreview() = PreviewTheme(dark = true) { ExtractionPreview(PARSE_FAILED_STATE) }

@Preview(name = "Extraction — landscape, light", showSystemUi = true, device = "spec:width=840dp,height=400dp,dpi=420")
@Composable
private fun ExtractionLandscapePreview() = PreviewTheme(dark = false) { ExtractionPreview(RUNNING_STATE) }
