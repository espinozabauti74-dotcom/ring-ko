package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.GloveItem

@Composable
fun GlovePreviewBadge(
    glove: GloveItem,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Guante ${glove.name}" }
    ) {
        val w = this.size.width
        val h = this.size.height

        // Subtle aura glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glove.glowColor.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = w * 0.5f
            ),
            radius = w * 0.5f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        rotate(degrees = -14f, pivot = Offset(w * 0.5f, h * 0.5f)) {
            // Wrist cuff
            drawRoundRect(
                color = glove.trimColor,
                topLeft = Offset(w * 0.28f, h * 0.62f),
                size = Size(w * 0.42f, h * 0.24f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
            )
            // Cuff stripe
            drawRect(
                color = glove.primaryColor,
                topLeft = Offset(w * 0.32f, h * 0.68f),
                size = Size(w * 0.34f, h * 0.05f)
            )

            // Thumb
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(glove.primaryColor, glove.primaryColor.copy(alpha = 0.78f))
                ),
                topLeft = Offset(w * 0.60f, h * 0.36f),
                size = Size(w * 0.22f, h * 0.30f),
                cornerRadius = CornerRadius(w * 0.11f, w * 0.11f)
            )

            // Main padded fist
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(glove.primaryColor, glove.primaryColor.copy(alpha = 0.74f)),
                    center = Offset(w * 0.40f, h * 0.32f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.20f, h * 0.16f),
                size = Size(w * 0.54f, h * 0.50f),
                cornerRadius = CornerRadius(w * 0.24f, w * 0.24f)
            )

            // Specular highlight
            drawOval(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(w * 0.27f, h * 0.22f),
                size = Size(w * 0.24f, h * 0.14f)
            )

            // Trim outline
            drawRoundRect(
                color = glove.trimColor.copy(alpha = 0.65f),
                topLeft = Offset(w * 0.20f, h * 0.16f),
                size = Size(w * 0.54f, h * 0.50f),
                cornerRadius = CornerRadius(w * 0.24f, w * 0.24f),
                style = Stroke(width = w * 0.03f)
            )
        }
    }
}
