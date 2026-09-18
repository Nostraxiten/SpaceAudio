package com.spaceaudio.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spaceaudio.app.ui.theme.NeonCyan
import com.spaceaudio.app.ui.theme.SpaceBlack

/**
 * 5-Minute Glowing NEW Badge component.
 * Displays with an electric neon aura and subtle pulse.
 */
@Composable
fun NewBadge(
    modifier: Modifier = Modifier,
    text: String = "NEW"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "new_pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_anim"
    )

    Box(
        modifier = modifier
            .alpha(alphaAnim)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(4.dp),
                ambientColor = NeonCyan,
                spotColor = NeonCyan
            )
            .clip(RoundedCornerShape(4.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        NeonCyan,
                        Color(0xFF00B0FF)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = SpaceBlack,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp
        )
    }
}
