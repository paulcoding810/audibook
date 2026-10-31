package com.paulcoding.audibook.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paulcoding.audibook.core.tts.VoiceWeight

@Composable
fun VoiceWeightBadge(weight: VoiceWeight, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) = when (weight) {
        VoiceWeight.LIGHT -> Triple("Light", colors.secondaryContainer, colors.onSecondaryContainer)
        VoiceWeight.HEAVY -> Triple("Heavy", colors.tertiaryContainer, colors.onTertiaryContainer)
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
