package com.llmlocal.feature.webclipper.presentation.images

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import com.llmlocal.core.designsystem.theme.AppTheme
import com.llmlocal.core.designsystem.theme.DesignTokens
import com.llmlocal.feature.webclipper.domain.input.ClipImage
import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import com.llmlocal.feature.webclipper.presentation.defaultWebClipperLabels
import java.io.File

/*
 * Previews of the images prepare/review screen: preparing, review with 2 and
 * 5 pictures, the no-model gate, the text-only-model gate, nothing readable,
 * an 840dp window and dark. Brand-free M3 baseline tokens; the files do not
 * exist, so tiles render as empty cards.
 */

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
private fun PreviewTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    AppTheme(tokens = PreviewTokens, darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

private fun pictures(n: Int) = List(n) { ClipImage(File("/preview/picture-$it.jpg"), 1_080, 2_400, "sha$it") }

private fun review(n: Int, max: Int = 3, selected: Int = 0, capped: Boolean = false) = ImagesClipState(
    images = pictures(n),
    selectedKey = "picture-$selected.jpg",
    maxImages = max,
    capped = capped,
)

@Composable
private fun Screen(state: ImagesClipState) =
    ImagesReviewScreen(state = state, labels = defaultWebClipperLabels(), onIntent = {})

@Preview(name = "Images — preparing", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun PreparingPreview() = PreviewTheme { Screen(ImagesClipState(isPreparing = true)) }

@Preview(name = "Images — review 2", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun Review2Preview() = PreviewTheme { Screen(review(2, selected = 1)) }

@Preview(name = "Images — review 5 (cap 5)", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun Review5Preview() = PreviewTheme { Screen(review(5, max = 5, selected = 2)) }

@Preview(name = "Images — capped warning", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun CappedPreview() = PreviewTheme { Screen(review(3, capped = true)) }

@Preview(name = "Images — no model", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun NoModelPreview() = PreviewTheme {
    Screen(review(2).copy(availability = ImagesAvailability.NoModel))
}

@Preview(name = "Images — text-only model", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun TextOnlyModelPreview() = PreviewTheme {
    Screen(review(2).copy(availability = ImagesAvailability.ModelLacksVision))
}

@Preview(name = "Images — nothing readable", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun NoneReadablePreview() = PreviewTheme { Screen(ImagesClipState(skipped = 2)) }

@Preview(name = "Images — wide 840dp", showBackground = true, widthDp = 840, heightDp = 600)
@Composable
private fun WidePreview() = PreviewTheme { Screen(review(3, selected = 1)) }

@Preview(
    name = "Images — review dark",
    showBackground = true,
    widthDp = 360,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DarkPreview() = PreviewTheme(dark = true) { Screen(review(3, selected = 0)) }
