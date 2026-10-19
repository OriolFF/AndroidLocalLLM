package com.llmlocal.recipekeeper.clip.presentation

import com.llmlocal.recipekeeper.clip.domain.StubDraftParser
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.llmlocal.recipekeeper.recipes.presentation.DefaultRecipeKeeperLibraryLabels
import com.llmlocal.recipekeeper.recipes.presentation.RecipeKeeperLibraryLabels
import com.llmlocal.core.designsystem.component.NumberBadge
import com.llmlocal.core.designsystem.component.SectionHeader
import com.llmlocal.core.designsystem.component.SkeletonLine
import com.llmlocal.core.designsystem.component.StreamingCursor
import com.llmlocal.recipekeeper.clip.domain.RecipeDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The extraction screen's `renderDraft` slot (UX spec §4): a recipe card
 * whose sections — Title → Meta → Ingredients → Steps → Notes — start as
 * skeleton lines and fill in as [parseDraft] promotes completed lines of
 * [streamedText]. The not-yet-parsed tail renders dimmed with a
 * [StreamingCursor]. The raw-output toggle is the feature's
 * (`WebClipperExtractionScreen`, driven by `ExtractionIntent.ToggleRaw`);
 * the card deliberately has none of its own (WS10 bug 5).
 *
 * Parsing is debounced ~80 ms off the main thread while the model is
 * running, and immediate once it stops. The card is **not** a live
 * region; one invisible text announces milestones (title found, n
 * ingredients, n steps) so TalkBack users hear progress without every
 * token being read out. Skeletons are invisible to accessibility; the
 * raw tail is stripped of semantics while running.
 *
 * @param parseDraft incremental parser; previews and this worktree use
 *   [StubDraftParser], WS5a's `RecipeKeeperDraftParser` is wired at merge
 */
@Composable
fun ClipDraftCard(
    streamedText: String,
    isRunning: Boolean,
    parseDraft: (String) -> RecipeDraft = StubDraftParser::parse,
    labels: RecipeKeeperLibraryLabels = DefaultRecipeKeeperLibraryLabels,
    modifier: Modifier = Modifier,
) {
    val initial = remember { parseDraft(streamedText) }
    val draft by produceState(initialValue = initial, streamedText, isRunning) {
        if (isRunning) delay(PARSE_DEBOUNCE_MS)
        value = withContext(Dispatchers.Default) { parseDraft(streamedText) }
    }
    val announcement = rememberMilestoneAnnouncement(draft, isRunning, labels)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TitleBlock(draft, isRunning)
            MetaBlock(draft, isRunning, labels)
            IngredientsBlock(draft, isRunning, labels)
            StepsBlock(draft, isRunning, labels)
            NotesBlock(draft, labels)
            TailBlock(streamedText, draft.tail, isRunning, labels)

            // Milestone live region — the only thing TalkBack hears unprompted.
            Text(
                text = announcement,
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private const val PARSE_DEBOUNCE_MS = 80L

/**
 * Announces: the title once it arrives, the ingredient count once the
 * steps start (ingredients are then complete), the step count once the
 * model stops.
 */
@Composable
private fun rememberMilestoneAnnouncement(
    draft: RecipeDraft,
    isRunning: Boolean,
    labels: RecipeKeeperLibraryLabels,
): String {
    var announcement by remember { mutableStateOf("") }
    val titleKnown = draft.title != null
    val ingredientsDone = draft.steps.isNotEmpty()
    val stepsDone = !isRunning && draft.steps.isNotEmpty()
    LaunchedEffect(titleKnown, ingredientsDone, stepsDone) {
        announcement = when {
            stepsDone -> labels.draftStepsFound(draft.steps.size)
            ingredientsDone -> labels.draftIngredientsFound(draft.ingredients.size)
            titleKnown -> labels.draftTitleFound(draft.title.orEmpty())
            else -> ""
        }
    }
    return announcement
}

@Composable
private fun TitleBlock(draft: RecipeDraft, isRunning: Boolean) {
    val title = draft.title
    when {
        title != null -> FadeIn {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        isRunning -> SkeletonLine(widthFraction = 0.65f, height = 22.dp)
    }
    val description = draft.description
    if (description != null) {
        FadeIn {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetaBlock(draft: RecipeDraft, isRunning: Boolean, labels: RecipeKeeperLibraryLabels) {
    val chips = buildList {
        draft.servings?.let { s -> add(s.trim().toIntOrNull()?.let(labels.servingsTemplate) ?: labels.servingsFreeform(s)) }
        draft.prepMinutes?.let { add(labels.prepTemplate(it)) }
        draft.cookMinutes?.let { add(labels.cookTemplate(it)) }
    }
    when {
        chips.isNotEmpty() -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { chip ->
                key(chip) {
                    FadeIn {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
        isRunning && draft.ingredients.isEmpty() -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(widthFraction = 0.22f, height = 28.dp)
            SkeletonLine(widthFraction = 0.28f, height = 28.dp)
        }
    }
}

@Composable
private fun IngredientsBlock(draft: RecipeDraft, isRunning: Boolean, labels: RecipeKeeperLibraryLabels) {
    if (draft.ingredients.isEmpty() && !isRunning) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(label = labels.ingredientsHeader, icon = Icons.Outlined.RestaurantMenu)
        draft.ingredients.forEachIndexed { index, ingredient ->
            key(index) {
                FadeIn {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Text(
                            text = ingredient,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        if (isRunning && draft.steps.isEmpty()) {
            val placeholders = if (draft.ingredients.isEmpty()) 3 else 1
            repeat(placeholders) { SkeletonLine(widthFraction = if (it % 2 == 0) 0.8f else 0.6f) }
        }
    }
}

@Composable
private fun StepsBlock(draft: RecipeDraft, isRunning: Boolean, labels: RecipeKeeperLibraryLabels) {
    if (draft.steps.isEmpty() && !isRunning) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(label = labels.stepsHeader, icon = Icons.Outlined.LocalFireDepartment)
        draft.steps.forEachIndexed { index, step ->
            key(index) {
                FadeIn {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        NumberBadge(n = index + 1)
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        if (isRunning && draft.notes == null) {
            val placeholders = if (draft.steps.isEmpty()) 2 else 1
            repeat(placeholders) { SkeletonLine(widthFraction = if (it % 2 == 0) 0.9f else 0.7f) }
        }
    }
}

@Composable
private fun NotesBlock(draft: RecipeDraft, labels: RecipeKeeperLibraryLabels) {
    val notes = draft.notes ?: return
    FadeIn {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(label = labels.notesHeader, icon = Icons.Outlined.Info)
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Dimmed unparsed tail + cursor while running; a "waiting" line before the first token. */
@Composable
private fun TailBlock(streamedText: String, tail: String, isRunning: Boolean, labels: RecipeKeeperLibraryLabels) {
    if (!isRunning) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (streamedText.isBlank()) {
            Text(
                text = labels.draftWaiting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (tail.isNotBlank()) {
            Text(
                text = tail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .alpha(0.6f)
                    .clearAndSetSemantics { },
            )
        }
        StreamingCursor()
    }
}

/** Fades + slides a row in the first time it appears. */
@Composable
private fun FadeIn(content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
    ) {
        content()
    }
}
