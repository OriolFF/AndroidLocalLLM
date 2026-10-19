package com.llmlocal.medical.medical.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.llmlocal.medical.R
import com.llmlocal.core.designsystem.component.SectionHeader
import com.llmlocal.core.designsystem.component.StreamingText
import com.llmlocal.medical.medical.domain.MedicalAdvice

/**
 * Card showing the generated medical advice. While the model is streaming
 * the raw cumulative text is shown with an animated primary-tinted cursor.
 * Once a structured [MedicalAdvice] is available, the UI swaps to a
 * formatted layout with one section per LLM-emitted heading.
 *
 * The streaming → structured transition crossfades via [AnimatedContent].
 *
 * The card is **not** a substitute for a clinician — see the disclaimer
 * block at the bottom of [MedicalAdvice.disclaimer], which is always
 * rendered here.
 *
 * [warmingUpLabel] comes from `MedicalLabels.warmingUpLabel` via `MedicalScreen`;
 * the card itself carries no copy.
 */
@Composable
fun StreamedMedicalCard(
    streamedText: String,
    advice: MedicalAdvice?,
    isGenerating: Boolean,
    warmingUpLabel: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        AnimatedContent(
            targetState = advice,
            transitionSpec = {
                if (targetState == null) {
                    fadeIn(animationSpec = tween(durationMillis = 200)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 200))
                } else {
                    fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 200))
                }
            },
            label = "medicalContent",
        ) { currentAdvice ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (currentAdvice != null) {
                    StructuredAdvice(currentAdvice)
                } else {
                    StreamingText(
                        text = streamedText,
                        isGenerating = isGenerating,
                        warmingUpLabel = warmingUpLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun StructuredAdvice(advice: MedicalAdvice) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ---- Condition (general category) --------------------------
        SectionHeader(
            label = stringResource(R.string.medical_card_possible_cause),
            icon = Icons.Filled.LocalHospital,
        )
        Text(
            text = advice.condition ?: stringResource(R.string.medical_card_condition_fallback),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // ---- Likely causes ------------------------------------------
        if (advice.likelyCauses.isNotEmpty()) {
            SectionHeader(
                label = stringResource(R.string.medical_card_likely_causes),
                icon = Icons.Filled.Info,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                advice.likelyCauses.forEach { cause ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Bullet(tone = BulletTone.Primary)
                        Text(
                            text = cause,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---- Self-care ----------------------------------------------
        if (advice.selfCare.isNotEmpty()) {
            SectionHeader(
                label = stringResource(R.string.medical_card_self_care),
                icon = Icons.Filled.Spa,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                advice.selfCare.forEach { suggestion ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Bullet(tone = BulletTone.Tertiary)
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ---- When to seek care (always rendered) ------------------
        SectionHeader(
            label = stringResource(R.string.medical_card_when_to_seek_care),
            icon = Icons.Filled.WarningAmber,
            iconTint = MaterialTheme.colorScheme.error,
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Text(
                text = advice.whenToSeekCare,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        // ---- Disclaimer (always rendered) --------------------------
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp),
            )
            Text(
                text = advice.disclaimer ?: stringResource(R.string.medical_disclaimer_fallback),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class BulletTone { Primary, Tertiary }

@Composable
private fun Bullet(tone: BulletTone) {
    val color = when (tone) {
        BulletTone.Primary -> MaterialTheme.colorScheme.primary
        BulletTone.Tertiary -> MaterialTheme.colorScheme.tertiary
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(color = color, shape = CircleShape),
    )
}
