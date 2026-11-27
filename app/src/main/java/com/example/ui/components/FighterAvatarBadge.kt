package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.FighterProfile
import com.example.model.HairStyle
import com.example.ui.theme.ArenaObsidian

@Composable
fun FighterAvatarBadge(
    fighter: FighterProfile,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    borderWidth: Dp = 2.dp
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(borderWidth, fighter.accentColor, CircleShape)
            .semantics { contentDescription = "Retrato de ${fighter.name}" }
    ) {
        val w = this.size.width
        val h = this.size.height

        // Arena spotlight backdrop
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(fighter.accentColor.copy(alpha = 0.35f), ArenaObsidian),
                center = Offset(w * 0.5f, h * 0.35f),
                radius = w * 0.75f
            )
        )

        // Shoulders
        drawRoundRect(
            color = fighter.skinTone.copy(alpha = 0.92f),
            topLeft = Offset(w * 0.18f, h * 0.68f),
            size = Size(w * 0.64f, h * 0.4f),
            cornerRadius = CornerRadius(w * 0.25f, w * 0.25f)
        )

        // Neck
        drawRect(
            color = fighter.skinTone.copy(alpha = 0.82f),
            topLeft = Offset(w * 0.41f, h * 0.56f),
            size = Size(w * 0.18f, h * 0.16f)
        )

        // Head
        val headW = w * 0.38f
        val headH = h * 0.44f
        val headLeft = (w - headW) / 2f
        val headTop = h * 0.18f
        drawRoundRect(
            color = fighter.skinTone,
            topLeft = Offset(headLeft, headTop),
            size = Size(headW, headH),
            cornerRadius = CornerRadius(headW * 0.45f, headW * 0.42f)
        )

        // Hair style
        when (fighter.hairStyle) {
            HairStyle.BUZZ -> {
                drawRoundRect(
                    color = fighter.hairColor,
                    topLeft = Offset(headLeft + headW * 0.05f, headTop - h * 0.01f),
                    size = Size(headW * 0.9f, headH * 0.28f),
                    cornerRadius = CornerRadius(headW * 0.4f, headW * 0.4f)
                )
            }
            HairStyle.MOHAWK -> {
                drawRoundRect(
                    color = fighter.hairColor,
                    topLeft = Offset(w * 0.44f, headTop - h * 0.08f),
                    size = Size(w * 0.12f, headH * 0.40f),
                    cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
                )
            }
            HairStyle.FADE_WAVES -> {
                val path = Path().apply {
                    moveTo(headLeft, headTop + headH * 0.3f)
                    cubicTo(
                        w * 0.35f, headTop - h * 0.06f,
                        w * 0.65f, headTop - h * 0.05f,
                        headLeft + headW, headTop + headH * 0.28f
                    )
                    lineTo(headLeft + headW, headTop + headH * 0.12f)
                    lineTo(headLeft, headTop + headH * 0.15f)
                    close()
                }
                drawPath(path, color = fighter.hairColor)
            }
            HairStyle.HEADBAND -> {
                drawRoundRect(
                    color = fighter.hairColor,
                    topLeft = Offset(headLeft + headW * 0.02f, headTop - h * 0.02f),
                    size = Size(headW * 0.96f, headH * 0.32f),
                    cornerRadius = CornerRadius(headW * 0.4f, headW * 0.4f)
                )
                drawRect(
                    color = fighter.gloveColor,
                    topLeft = Offset(headLeft - w * 0.01f, headTop + headH * 0.18f),
                    size = Size(headW + w * 0.02f, h * 0.065f)
                )
            }
        }

        // Determined brows & eyes
        val eyeY = headTop + headH * 0.45f
        drawLine(
            color = Color(0xFF1A1A1A),
            start = Offset(w * 0.39f, eyeY - h * 0.04f),
            end = Offset(w * 0.47f, eyeY - h * 0.02f),
            strokeWidth = w * 0.032f
        )
        drawLine(
            color = Color(0xFF1A1A1A),
            start = Offset(w * 0.61f, eyeY - h * 0.04f),
            end = Offset(w * 0.53f, eyeY - h * 0.02f),
            strokeWidth = w * 0.032f
        )
        drawCircle(color = Color(0xFF141414), radius = w * 0.026f, center = Offset(w * 0.43f, eyeY))
        drawCircle(color = Color(0xFF141414), radius = w * 0.026f, center = Offset(w * 0.57f, eyeY))

        // Foreground gloves tucked at chin
        drawCircle(
            color = fighter.gloveColor,
            radius = w * 0.135f,
            center = Offset(w * 0.28f, h * 0.72f)
        )
        drawCircle(
            color = fighter.accentColor.copy(alpha = 0.55f),
            radius = w * 0.135f,
            center = Offset(w * 0.28f, h * 0.72f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.02f)
        )
        drawCircle(
            color = fighter.gloveColor,
            radius = w * 0.135f,
            center = Offset(w * 0.72f, h * 0.72f)
        )
        drawCircle(
            color = fighter.accentColor.copy(alpha = 0.55f),
            radius = w * 0.135f,
            center = Offset(w * 0.72f, h * 0.72f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.02f)
        )
    }
}
