package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.example.model.HandSide
import com.example.model.PunchType
import kotlin.math.PI
import kotlin.math.sin

private val BOXER_BODY_GRID: List<String> = listOf(
    "...........KKKKKK.........", // 0
    "..........KHHHHHHK........", // 1
    "..........KHHHHHHK........", // 2
    ".........KKSSSSSSKK.......", // 3
    ".........KSSSESESSK.......", // 4
    ".........KDSLLLLSDK.......", // 5
    "..........KSSLLSSK........", // 6
    "..........KKDDDDKK........", // 7
    "........KKKKSSSSKKKK......", // 8
    "......KKLLLLSSSSLLLLKK....", // 9
    ".....KLLSSSDSSSSSDSSLLK...", // 10
    "....KLLSDSSSDSSLLDSSKLLK..", // 11
    "....KLSDKSSSSLLSDSSSKLDK..", // 12
    "....KSSKSSSDSSLLDSSSKSSK..", // 13
    ".....KKKSSSSDSSSDSSSKKK...", // 14
    ".......KSSSDLLLLDSSSK.....", // 15
    ".......KSSSDSSSSDSSSK.....", // 16
    "......KKBBBBBBBBBBBBKK....", // 17
    "......KBBBBWWBBBBBBBBK....", // 18
    ".....KKWWWWWWWWWWWWWWKK...", // 19
    "....KTTWWWWWWWWWWWWWWTTK..", // 20
    "....KTWWWWWWwwwwWWWWWWwK..", // 21
    "....KWWWWwwKKKKKKwwWWWWK..", // 22
    "....KKKKKKK......KKKKKKK..", // 23
    "...KLLSSDK........KLLSSDK.", // 24
    "...KSSSDKK........KKSSSDK.", // 25
    "..KLLSDK............KSSSDK", // 26
    "..KSSSDK............KKSSDK", // 27
    "..KSSDK..............KSSDK", // 28
    "..KWWWK..............KWWWK", // 29
    "..KWwWK..............KWWwK", // 30
    "..KWWWK..............KWWwK", // 31
    "..KWWWwK............KWWWwK", // 32
    "..KKKKKK............KKKKKK"  // 33
)

private val PIXEL_GLOVE_GRID: List<String> = listOf(
    ".KKKKK.",
    "KhhGGGK",
    "KhGGGGK",
    "KGGGGgK",
    "KGggggK",
    ".KKKKK."
)

fun DrawScope.drawPixelArtBoxer(
    centerX: Float,
    bottomY: Float,
    spriteHeight: Float,
    facingRight: Boolean,
    skinBase: Color = Color(0xFFC47A4C),
    hairColor: Color = Color(0xFF4A2C1D),
    trunksColor: Color = Color(0xFFD62828),
    gloveColor: Color = Color(0xFFE63946),
    isGuarding: Boolean = false,
    isDodging: Boolean = false,
    isKnockedDown: Boolean = false,
    activePunch: PunchType? = null,
    punchProgress: Float = 0f,
    hitFlashAlpha: Float = 0f,
    bobOffsetPx: Float = 0f
) {
    val gridCols = 26
    val gridRows = BOXER_BODY_GRID.size
    val px = spriteHeight / gridRows.toFloat()
    val spriteWidth = gridCols * px
    val dir = if (facingRight) 1f else -1f

    val dodgeShiftX = if (isDodging) -dir * px * 3.5f else 0f
    val dropY = if (isKnockedDown) spriteHeight * 0.28f else 0f
    val originX = centerX - spriteWidth / 2f + dodgeShiftX
    val originY = bottomY - spriteHeight + bobOffsetPx + dropY

    val outline = Color(0xFF120E18)
    val skinLight = lerp(skinBase, Color.White, 0.28f)
    val skinDark = lerp(skinBase, Color(0xFF2B1205), 0.34f)
    val flashMix = hitFlashAlpha.coerceIn(0f, 1f)

    fun maybeFlash(base: Color): Color {
        return if (flashMix > 0.05f) lerp(base, Color.White, flashMix * 0.78f) else base
    }

    // Ground shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.38f),
        topLeft = Offset(originX + px * 2f, bottomY - px * 1.4f),
        size = Size(spriteWidth - px * 4f, px * 2.4f)
    )

    // Draw body pixel grid
    for (r in 0 until gridRows) {
        val row = BOXER_BODY_GRID[r]
        val y = originY + r * px
        for (c in 0 until row.length) {
            val ch = row[c]
            if (ch == '.') continue
            val colIdx = if (facingRight) c else (gridCols - 1 - c)
            val x = originX + colIdx * px
            val cellColor = when (ch) {
                'K' -> outline
                'H' -> maybeFlash(hairColor)
                'S' -> maybeFlash(skinBase)
                'L' -> maybeFlash(skinLight)
                'D' -> maybeFlash(skinDark)
                'E' -> outline
                'B' -> maybeFlash(trunksColor)
                'T' -> maybeFlash(trunksColor)
                'W' -> maybeFlash(Color(0xFFF4F6FA))
                'w' -> maybeFlash(Color(0xFFB5C2D6))
                else -> continue
            }
            drawRect(
                color = cellColor,
                topLeft = Offset(x, y),
                size = Size(px + 0.75f, px + 0.75f)
            )
        }
    }

    // Dynamic Pixel Gloves + Forearms
    val thrust = if (activePunch != null) sin(punchProgress * PI.toFloat()).coerceIn(0f, 1f) else 0f
    val punchReachPx = when (activePunch) {
        PunchType.JAB, PunchType.CROSS -> px * 10.5f
        PunchType.HOOK -> px * 8.5f
        PunchType.UPPERCUT -> px * 7.5f
        null -> 0f
    }
    val uppercutLift = if (activePunch == PunchType.UPPERCUT) -px * 4.5f * thrust else 0f
    val guardLift = if (isGuarding) -px * 3.0f else 0f

    // Rear glove (lead hand in front-right 3/4 stance sits further forward)
    val isRightPunch = activePunch?.side == HandSide.RIGHT
    val isLeftPunch = activePunch?.side == HandSide.LEFT

    val backArmX = originX + (if (facingRight) 10.5f else 9.5f) * px +
        (if (isLeftPunch) dir * punchReachPx * thrust else 0f)
    val backArmY = originY + 10.5f * px + guardLift + (if (isLeftPunch) uppercutLift else 0f)

    val frontArmX = originX + (if (facingRight) 15.5f else 4.5f) * px +
        (if (isRightPunch) dir * punchReachPx * thrust else 0f)
    val frontArmY = originY + 9.2f * px + guardLift + (if (isRightPunch) uppercutLift else 0f)

    // Draw extended pixel forearm if punching
    if (thrust > 0.08f) {
        val shoulderX = originX + (if (facingRight) 14f else 12f) * px
        val shoulderY = originY + 11.5f * px
        val targetX = if (isRightPunch) frontArmX + 2.5f * px else backArmX + 2.5f * px
        val targetY = if (isRightPunch) frontArmY + 2.5f * px else backArmY + 2.5f * px
        val steps = 5
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val ax = shoulderX + (targetX - shoulderX) * t
            val ay = shoulderY + (targetY - shoulderY) * t
            drawRect(
                color = outline,
                topLeft = Offset(ax - px * 1.4f, ay - px * 1.4f),
                size = Size(px * 2.8f, px * 2.8f)
            )
            drawRect(
                color = maybeFlash(skinBase),
                topLeft = Offset(ax - px * 0.9f, ay - px * 0.9f),
                size = Size(px * 1.8f, px * 1.8f)
            )
        }
    }

    drawPixelGlove(
        topLeftX = backArmX,
        topLeftY = backArmY,
        px = px,
        facingRight = facingRight,
        gloveColor = maybeFlash(gloveColor),
        outline = outline
    )
    drawPixelGlove(
        topLeftX = frontArmX,
        topLeftY = frontArmY,
        px = px,
        facingRight = facingRight,
        gloveColor = maybeFlash(gloveColor),
        outline = outline
    )
}

private fun DrawScope.drawPixelGlove(
    topLeftX: Float,
    topLeftY: Float,
    px: Float,
    facingRight: Boolean,
    gloveColor: Color,
    outline: Color
) {
    val cols = 7
    val gloveLight = lerp(gloveColor, Color.White, 0.32f)
    val gloveDark = lerp(gloveColor, Color.Black, 0.34f)
    for (r in PIXEL_GLOVE_GRID.indices) {
        val row = PIXEL_GLOVE_GRID[r]
        val y = topLeftY + r * px
        for (c in row.indices) {
            val ch = row[c]
            if (ch == '.') continue
            val colIdx = if (facingRight) c else (cols - 1 - c)
            val x = topLeftX + colIdx * px
            val color = when (ch) {
                'K' -> outline
                'h' -> gloveLight
                'G' -> gloveColor
                'g' -> gloveDark
                else -> continue
            }
            drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(px + 0.75f, px + 0.75f)
            )
        }
    }
}
