package com.example.ui.fight

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.example.model.CalloutTone
import com.example.model.DodgeDirection
import com.example.model.EnemyAttackType
import com.example.model.EnemyPhase
import com.example.model.FightPhase
import com.example.model.FightUiState
import com.example.model.HairStyle
import com.example.model.HandSide
import com.example.model.PlayerAction
import com.example.model.PunchType
import com.example.ui.theme.ArenaObsidian
import com.example.ui.theme.ArenaSlate
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CobaltCorner
import com.example.ui.theme.CobaltGlow
import com.example.ui.theme.CrimsonCorner
import com.example.ui.theme.StaminaEmerald
import com.example.ui.theme.WarningAmber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RingCanvas(
    state: FightUiState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ring_idle")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breath"
    )

    val dodgeSwayTarget = when (state.dodgeDirection) {
        DodgeDirection.LEFT -> 1f
        DodgeDirection.RIGHT -> -1f
        DodgeDirection.NONE -> 0f
    }
    val cameraSway by animateFloatAsState(
        targetValue = dodgeSwayTarget,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "dodge_sway"
    )

    val guardAnim by animateFloatAsState(
        targetValue = if (state.isGuarding || state.playerAction == PlayerAction.GUARD) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "guard_anim"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val breath = sin(animPhase)
            val shakeAmp = state.screenShake * w * 0.025f
            val shakeX = sin(animPhase * 11f) * shakeAmp
            val shakeY = cos(animPhase * 13f) * shakeAmp

            // Smooth horizontal parallax as player moves across the ring
            val cameraPanX = -state.playerPosX * w * 0.22f
            val worldOffsetX = cameraPanX + cameraSway * w * 0.085f + shakeX
            val worldOffsetY = shakeY

            // 1. Arena backdrop & spotlight beams (sweeping during presentation!)
            drawArenaBackdrop(
                w = w,
                h = h,
                counterActive = state.counterWindowMs > 0L,
                isPresentation = state.phase == FightPhase.PRESENTATION,
                animPhase = animPhase
            )

            // 2. Animated Multi-Tier Crowd (Hinchada)
            drawAnimatedCrowd(
                w = w,
                h = h,
                parallaxX = worldOffsetX * 0.22f,
                excitement = state.crowdExcitement,
                animPhase = animPhase
            )

            // 3. Ring ropes, turnbuckles & canvas mat with footwork grid
            drawRingEnvironment(
                w = w,
                h = h,
                offsetX = worldOffsetX * 0.70f,
                offsetY = worldOffsetY * 0.45f,
                accent = state.fighter.accentColor
            )

            // 4. Opponent boxer (positioned by lateral delta & scaled by relative distance!)
            val opponentOffsetX = worldOffsetX + state.enemyPosX * w * 0.34f
            val distanceScale = (1.16f - state.relativeDistance * 0.38f).coerceIn(0.76f, 1.18f)
            drawOpponentBoxer(
                state = state,
                w = w,
                h = h,
                offsetX = opponentOffsetX,
                offsetY = worldOffsetY,
                distanceScale = distanceScale,
                breath = breath,
                animPhase = animPhase
            )

            // 5. Impact Sparks
            state.sparks.forEach { spark ->
                val t = (spark.ageMs / spark.durationMs).coerceIn(0f, 1f)
                val dist = spark.speed * w * t
                val cx = spark.xNorm * w + cos(spark.angleRad) * dist
                val cy = spark.yNorm * h + sin(spark.angleRad) * dist
                val sparkColor = toneColor(spark.tone).copy(alpha = (1f - t))
                drawCircle(
                    color = sparkColor,
                    radius = (w * 0.012f) * (1f - t * 0.6f),
                    center = Offset(cx, cy)
                )
            }

            // 6. Player foreground gloves (using equipped shop glove!)
            drawPlayerGloves(
                state = state,
                w = w,
                h = h,
                breath = breath,
                guardAnim = guardAnim,
                cameraSway = cameraSway,
                shakeX = shakeX,
                shakeY = shakeY
            )

            // 7. Hit flash / counter vignette
            if (state.playerHitFlash > 0.02f) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, CrimsonCorner.copy(alpha = state.playerHitFlash * 0.55f)),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = w * 0.85f
                    )
                )
            } else if (state.counterWindowMs > 0L) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, ChampionshipGold.copy(alpha = 0.22f)),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = w * 0.9f
                    )
                )
            }
        }

        // 8. Crisp Floating Callouts overlaid smoothly
        CalloutsOverlay(state = state)
    }
}

@Composable
private fun CalloutsOverlay(state: FightUiState) {
    Layout(
        content = {
            state.callouts.forEach { c ->
                val progress = (c.ageMs / c.durationMs).coerceIn(0f, 1f)
                val alpha = (1f - progress * progress).coerceIn(0f, 1f)
                Text(
                    text = c.text,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = toneColor(c.tone).copy(alpha = alpha)
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { idx, placeable ->
                val callout = state.callouts.getOrNull(idx) ?: return@forEachIndexed
                val progress = (callout.ageMs / callout.durationMs).coerceIn(0f, 1f)
                val risePx = (progress * constraints.maxHeight * 0.075f)
                val x = (callout.xNorm * constraints.maxWidth - placeable.width / 2f).roundToInt()
                val y = (callout.yNorm * constraints.maxHeight - placeable.height / 2f - risePx).roundToInt()
                placeable.placeRelative(IntOffset(x, y))
            }
        }
    }
}

private fun DrawScope.drawArenaBackdrop(
    w: Float,
    h: Float,
    counterActive: Boolean,
    isPresentation: Boolean,
    animPhase: Float
) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(ArenaObsidian, Color(0xFF121728), ArenaSlate)
        )
    )

    val sweep = if (isPresentation) sin(animPhase) * w * 0.14f else 0f
    val beamAlpha = when {
        isPresentation -> 0.28f
        counterActive -> 0.24f
        else -> 0.15f
    }

    val leftBeam = Path().apply {
        moveTo(w * 0.16f + sweep, 0f)
        lineTo(w * 0.30f + sweep, 0f)
        lineTo(w * 0.68f - sweep * 0.5f, h * 0.85f)
        lineTo(w * 0.22f - sweep * 0.5f, h * 0.85f)
        close()
    }
    drawPath(
        path = leftBeam,
        brush = Brush.verticalGradient(
            colors = listOf(ChalkWhite.copy(alpha = beamAlpha), Color.Transparent),
            startY = 0f,
            endY = h * 0.85f
        )
    )

    val rightBeam = Path().apply {
        moveTo(w * 0.70f - sweep, 0f)
        lineTo(w * 0.84f - sweep, 0f)
        lineTo(w * 0.78f + sweep * 0.5f, h * 0.85f)
        lineTo(w * 0.32f + sweep * 0.5f, h * 0.85f)
        close()
    }
    drawPath(
        path = rightBeam,
        brush = Brush.verticalGradient(
            colors = listOf(ChampionshipGold.copy(alpha = beamAlpha * 0.88f), Color.Transparent),
            startY = 0f,
            endY = h * 0.85f
        )
    )
}

private fun DrawScope.drawAnimatedCrowd(
    w: Float,
    h: Float,
    parallaxX: Float,
    excitement: Float,
    animPhase: Float
) {
    val fanPalette = listOf(
        CrimsonCorner.copy(alpha = 0.72f),
        CobaltCorner.copy(alpha = 0.72f),
        ChampionshipGold.copy(alpha = 0.72f),
        Color(0xFF334155),
        ChalkWhite.copy(alpha = 0.55f),
        StaminaEmerald.copy(alpha = 0.60f)
    )

    // Back tier & Front tier of cheering fans
    for (tier in 0..1) {
        val baseY = if (tier == 0) h * 0.46f else h * 0.54f
        val count = if (tier == 0) 20 else 16
        val sizeScale = if (tier == 0) 0.78f else 1.0f
        val rowAlpha = if (tier == 0) 0.55f else 0.85f

        for (i in -2..count + 2) {
            val idx = i + tier * 7
            val cx = ((i.toFloat() / count) * w * 1.15f) - w * 0.07f + parallaxX * (0.6f + tier * 0.4f)
            val jumpSpeed = 2f + (idx % 3) + excitement * 4f
            val bounce = abs(sin(animPhase * jumpSpeed + idx * 0.9f)) * h * (0.006f + excitement * 0.028f)
            val cy = baseY - bounce
            val headR = w * 0.020f * sizeScale
            val shirtColor = fanPalette[abs(idx) % fanPalette.size].copy(alpha = rowAlpha)

            // Raised cheering arms when excited!
            if (excitement > 0.38f || idx % 4 == 0) {
                val wave = sin(animPhase * (3f + excitement * 5f) + idx) * headR * 0.7f
                drawLine(
                    color = shirtColor,
                    start = Offset(cx - headR * 0.9f, cy + headR * 0.9f),
                    end = Offset(cx - headR * 1.4f, cy - headR * (0.6f + excitement * 0.8f) + wave),
                    strokeWidth = headR * 0.42f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = shirtColor,
                    start = Offset(cx + headR * 0.9f, cy + headR * 0.9f),
                    end = Offset(cx + headR * 1.4f, cy - headR * (0.6f + excitement * 0.8f) - wave),
                    strokeWidth = headR * 0.42f,
                    cap = StrokeCap.Round
                )
            }

            // Torso
            drawRoundRect(
                color = shirtColor,
                topLeft = Offset(cx - headR * 1.25f, cy + headR * 0.65f),
                size = Size(headR * 2.5f, h * 0.11f * sizeScale),
                cornerRadius = CornerRadius(headR * 0.8f, headR * 0.8f)
            )
            // Head
            drawCircle(
                color = Color(0xFF1E2433).copy(alpha = rowAlpha),
                radius = headR,
                center = Offset(cx, cy)
            )

            // Phone lights / camera flashes in crowd during hype!
            if (excitement > 0.58f && abs(sin(animPhase * 6f + idx * 1.7f)) > 0.92f) {
                drawCircle(
                    color = ChalkWhite.copy(alpha = 0.9f),
                    radius = headR * 0.45f,
                    center = Offset(cx + headR * 1.1f, cy - headR * 0.9f)
                )
            }
        }
    }
}

private fun DrawScope.drawRingEnvironment(
    w: Float,
    h: Float,
    offsetX: Float,
    offsetY: Float,
    accent: Color
) {
    val matTopY = h * 0.60f + offsetY
    val matPath = Path().apply {
        moveTo(0f, matTopY)
        lineTo(w, matTopY)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(
        path = matPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF1F2738), Color(0xFF141824), ArenaObsidian),
            startY = matTopY,
            endY = h
        )
    )

    // Center mat ring emblem & floor lines so lateral motion is vividly felt
    drawOval(
        color = accent.copy(alpha = 0.26f),
        topLeft = Offset(w * 0.22f + offsetX, h * 0.72f + offsetY),
        size = Size(w * 0.56f, h * 0.14f),
        style = Stroke(width = w * 0.008f)
    )
    drawLine(
        color = ChalkWhite.copy(alpha = 0.08f),
        start = Offset(w * 0.50f + offsetX, matTopY),
        end = Offset(w * 0.50f + offsetX, h),
        strokeWidth = w * 0.006f
    )

    // Corner turnbuckles (positioned wider so panning reveals Red/Blue corners!)
    drawRoundRect(
        color = CrimsonCorner,
        topLeft = Offset(-w * 0.06f + offsetX, h * 0.30f + offsetY),
        size = Size(w * 0.065f, h * 0.34f),
        cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
    )
    drawRoundRect(
        color = CobaltCorner,
        topLeft = Offset(w * 0.995f + offsetX, h * 0.30f + offsetY),
        size = Size(w * 0.065f, h * 0.34f),
        cornerRadius = CornerRadius(w * 0.02f, w * 0.02f)
    )

    // Three taut ring ropes extending across wide ring canvas
    val ropeColors = listOf(
        CrimsonCorner.copy(alpha = 0.80f),
        ChalkWhite.copy(alpha = 0.75f),
        CobaltCorner.copy(alpha = 0.80f)
    )
    val ropeYs = listOf(h * 0.35f, h * 0.44f, h * 0.53f)
    ropeYs.forEachIndexed { idx, baseY ->
        drawLine(
            color = ropeColors[idx],
            start = Offset(-w * 0.05f + offsetX, baseY + offsetY),
            end = Offset(w * 1.05f + offsetX, baseY + offsetY),
            strokeWidth = h * 0.011f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawOpponentBoxer(
    state: FightUiState,
    w: Float,
    h: Float,
    offsetX: Float,
    offsetY: Float,
    distanceScale: Float,
    breath: Float,
    animPhase: Float
) {
    val fighter = state.fighter
    val isDown = state.enemyPhase == EnemyPhase.KNOCKED_DOWN
    val isHit = state.enemyPhase == EnemyPhase.HIT_STUN
    val isGuarding = state.enemyPhase == EnemyPhase.GUARDING
    val isTelegraph = state.enemyPhase == EnemyPhase.TELEGRAPH
    val isStriking = state.enemyPhase == EnemyPhase.STRIKING
    val isVulnerable = state.enemyPhase == EnemyPhase.VULNERABLE
    val isPresentationWarmup = state.phase == FightPhase.PRESENTATION && state.presentationStep == 1

    val downDropY = if (isDown) h * 0.28f else 0f
    val hitSnapX = if (isHit) (if (state.activePunch?.side == HandSide.LEFT) w * 0.035f else -w * 0.035f) else 0f
    val hitSnapY = if (isHit) -h * 0.018f else 0f

    val cx = w * 0.50f + offsetX + hitSnapX + breath * w * 0.008f
    val bodyScale = (if (isDown) 0.88f else 1f) * distanceScale
    val baseTopY = h * 0.19f + (1f - distanceScale) * h * 0.14f
    val topY = baseTopY + offsetY + hitSnapY + downDropY + abs(breath) * h * 0.012f
    val torsoW = w * 0.32f * bodyScale
    val torsoH = h * 0.26f * bodyScale

    // Ground shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.45f),
        topLeft = Offset(cx - torsoW * 0.7f, topY + torsoH * 1.55f),
        size = Size(torsoW * 1.4f, h * 0.06f)
    )

    // Trunks / Waist
    if (!isDown) {
        drawRoundRect(
            color = fighter.trunksColor,
            topLeft = Offset(cx - torsoW * 0.42f, topY + torsoH * 1.15f),
            size = Size(torsoW * 0.84f, h * 0.17f * bodyScale),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
        drawRect(
            color = ChampionshipGold,
            topLeft = Offset(cx - torsoW * 0.42f, topY + torsoH * 1.15f),
            size = Size(torsoW * 0.84f, h * 0.025f * bodyScale)
        )
    }

    // Torso & broad shoulders
    val torsoTop = topY + torsoH * 0.48f
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(fighter.skinTone, fighter.skinTone.copy(alpha = 0.82f))
        ),
        topLeft = Offset(cx - torsoW * 0.50f, torsoTop),
        size = Size(torsoW, torsoH * 0.78f),
        cornerRadius = CornerRadius(torsoW * 0.28f, torsoW * 0.18f)
    )

    // Neck
    drawRect(
        color = fighter.skinTone.copy(alpha = 0.90f),
        topLeft = Offset(cx - torsoW * 0.14f, topY + torsoH * 0.34f),
        size = Size(torsoW * 0.28f, torsoH * 0.20f)
    )

    // Head
    val headW = w * 0.18f * bodyScale
    val headH = h * 0.135f * bodyScale
    val headLeft = cx - headW * 0.5f
    val headTop = topY

    rotate(degrees = if (isHit) -8f else if (isDown) 14f else 0f, pivot = Offset(cx, headTop + headH * 0.5f)) {
        if (isVulnerable) {
            drawCircle(
                color = ChampionshipGold.copy(alpha = 0.25f),
                radius = headW * 0.85f,
                center = Offset(cx, headTop + headH * 0.5f)
            )
        }

        drawRoundRect(
            color = if (state.enemyHitFlash > 0.25f) Color.White.copy(alpha = 0.85f) else fighter.skinTone,
            topLeft = Offset(headLeft, headTop),
            size = Size(headW, headH),
            cornerRadius = CornerRadius(headW * 0.45f, headW * 0.42f)
        )

        when (fighter.hairStyle) {
            HairStyle.BUZZ -> {
                drawRoundRect(
                    color = fighter.hairColor,
                    topLeft = Offset(headLeft + headW * 0.06f, headTop - headH * 0.04f),
                    size = Size(headW * 0.88f, headH * 0.26f),
                    cornerRadius = CornerRadius(headW * 0.35f, headW * 0.35f)
                )
            }
            HairStyle.MOHAWK -> {
                drawRoundRect(
                    color = fighter.hairColor,
                    topLeft = Offset(cx - headW * 0.16f, headTop - headH * 0.18f),
                    size = Size(headW * 0.32f, headH * 0.36f),
                    cornerRadius = CornerRadius(headW * 0.12f, headW * 0.12f)
                )
            }
            HairStyle.FADE_WAVES -> {
                drawRoundRect(
                    color = fighter.hairColor,
                    topLeft = Offset(headLeft, headTop - headH * 0.08f),
                    size = Size(headW, headH * 0.30f),
                    cornerRadius = CornerRadius(headW * 0.38f, headW * 0.28f)
                )
            }
            HairStyle.HEADBAND -> {
                drawRoundRect(
                    color = fighter.hairColor,
                    topLeft = Offset(headLeft + headW * 0.02f, headTop - headH * 0.05f),
                    size = Size(headW * 0.96f, headH * 0.30f),
                    cornerRadius = CornerRadius(headW * 0.35f, headW * 0.35f)
                )
                drawRect(
                    color = fighter.gloveColor,
                    topLeft = Offset(headLeft - w * 0.004f, headTop + headH * 0.16f),
                    size = Size(headW + w * 0.008f, headH * 0.14f)
                )
            }
        }

        val eyeY = headTop + headH * 0.48f
        if (isHit || isDown) {
            drawLine(
                color = Color(0xFF1A1A1A),
                start = Offset(cx - headW * 0.28f, eyeY - headH * 0.04f),
                end = Offset(cx - headW * 0.12f, eyeY + headH * 0.03f),
                strokeWidth = w * 0.007f * bodyScale
            )
            drawLine(
                color = Color(0xFF1A1A1A),
                start = Offset(cx + headW * 0.28f, eyeY - headH * 0.04f),
                end = Offset(cx + headW * 0.12f, eyeY + headH * 0.03f),
                strokeWidth = w * 0.007f * bodyScale
            )
        } else {
            drawLine(
                color = Color(0xFF181818),
                start = Offset(cx - headW * 0.28f, eyeY - headH * 0.10f),
                end = Offset(cx - headW * 0.10f, eyeY - headH * 0.04f),
                strokeWidth = w * 0.007f * bodyScale,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF181818),
                start = Offset(cx + headW * 0.28f, eyeY - headH * 0.10f),
                end = Offset(cx + headW * 0.10f, eyeY - headH * 0.04f),
                strokeWidth = w * 0.007f * bodyScale,
                cap = StrokeCap.Round
            )
            drawCircle(color = Color(0xFF121212), radius = headW * 0.055f, center = Offset(cx - headW * 0.19f, eyeY))
            drawCircle(color = Color(0xFF121212), radius = headW * 0.055f, center = Offset(cx + headW * 0.19f, eyeY))
        }

        drawRoundRect(
            color = if (isHit) Color(0xFFF8FAFC) else Color(0xFFE2E8F0),
            topLeft = Offset(cx - headW * 0.15f, headTop + headH * 0.76f),
            size = Size(headW * 0.30f, headH * 0.09f),
            cornerRadius = CornerRadius(headW * 0.05f, headW * 0.05f)
        )
    }

    // Opponent Arms & Gloves
    if (!isDown) {
        val attack = state.enemyAttack
        val prog = state.enemyPhaseProgress
        val attackingLeftArm = attack == EnemyAttackType.LEFT_JAB || attack == EnemyAttackType.LEFT_HOOK
        val attackingRightArm = attack == EnemyAttackType.RIGHT_CROSS || attack == EnemyAttackType.UPPERCUT

        val leftShoulder = Offset(cx - torsoW * 0.45f, torsoTop + torsoH * 0.14f)
        val rightShoulder = Offset(cx + torsoW * 0.45f, torsoTop + torsoH * 0.14f)

        val warmupShift = if (isPresentationWarmup) sin(animPhase * 4f) * torsoW * 0.18f else 0f
        var leftGlovePos = Offset(cx - torsoW * 0.34f + warmupShift, headTop + headH * 0.92f + breath * h * 0.012f)
        var rightGlovePos = Offset(cx + torsoW * 0.34f - warmupShift, headTop + headH * 0.92f - breath * h * 0.012f)
        var leftGloveScale = 1f
        var rightGloveScale = 1f

        when {
            isGuarding -> {
                leftGlovePos = Offset(cx - headW * 0.46f, headTop + headH * 0.56f)
                rightGlovePos = Offset(cx + headW * 0.46f, headTop + headH * 0.56f)
            }
            isVulnerable -> {
                leftGlovePos = Offset(cx - torsoW * 0.65f, torsoTop + torsoH * 0.55f)
                rightGlovePos = Offset(cx + torsoW * 0.65f, torsoTop + torsoH * 0.55f)
            }
            isTelegraph -> {
                if (attackingLeftArm) {
                    leftGlovePos = Offset(
                        cx - torsoW * (0.55f + prog * 0.42f),
                        headTop + headH * (0.85f + if (attack == EnemyAttackType.LEFT_HOOK) 0.12f else 0f)
                    )
                    leftGloveScale = 1.08f
                }
                if (attackingRightArm) {
                    val dropY = if (attack == EnemyAttackType.UPPERCUT) h * 0.10f * prog else 0f
                    rightGlovePos = Offset(
                        cx + torsoW * (0.55f + prog * 0.42f),
                        headTop + headH * 0.85f + dropY
                    )
                    rightGloveScale = 1.08f
                }
            }
            isStriking -> {
                val thrust = sin(prog * PI.toFloat()).coerceIn(0f, 1f)
                if (attackingLeftArm) {
                    leftGlovePos = Offset(
                        cx - torsoW * 0.25f * (1f - thrust) + w * 0.04f * thrust,
                        headTop + headH * 0.9f + h * 0.19f * thrust
                    )
                    leftGloveScale = 1f + thrust * 1.38f
                }
                if (attackingRightArm) {
                    rightGlovePos = Offset(
                        cx + torsoW * 0.25f * (1f - thrust) - w * 0.04f * thrust,
                        headTop + headH * 0.9f + h * 0.19f * thrust
                    )
                    rightGloveScale = 1f + thrust * 1.38f
                }
            }
        }

        drawLine(
            color = fighter.skinTone.copy(alpha = 0.92f),
            start = leftShoulder,
            end = leftGlovePos,
            strokeWidth = w * 0.064f * bodyScale,
            cap = StrokeCap.Round
        )
        drawLine(
            color = fighter.skinTone.copy(alpha = 0.92f),
            start = rightShoulder,
            end = rightGlovePos,
            strokeWidth = w * 0.064f * bodyScale,
            cap = StrokeCap.Round
        )

        if (isTelegraph) {
            val warnCenter = if (attackingLeftArm) leftGlovePos else rightGlovePos
            val warnColor = if (attack?.isHeavy == true) CrimsonCorner else WarningAmber
            drawCircle(
                color = warnColor.copy(alpha = 0.32f + prog * 0.45f),
                radius = w * (0.085f + (1f - prog) * 0.055f) * bodyScale,
                center = warnCenter,
                style = Stroke(width = w * 0.011f)
            )
            val arrowX = if (attack?.expectedDodge == HandSide.LEFT) warnCenter.x - w * 0.10f else warnCenter.x + w * 0.10f
            drawCircle(
                color = warnColor.copy(alpha = 0.85f),
                radius = w * 0.015f,
                center = Offset(arrowX, warnCenter.y)
            )
        }

        if (isGuarding) {
            drawArc(
                color = CobaltGlow.copy(alpha = 0.45f),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - headW * 0.85f, headTop + headH * 0.25f),
                size = Size(headW * 1.7f, headH * 0.9f),
                style = Stroke(width = w * 0.010f)
            )
        }

        val baseRadius = w * 0.064f * bodyScale
        drawBoxingGlove(leftGlovePos, baseRadius * leftGloveScale, fighter.gloveColor, fighter.accentColor)
        drawBoxingGlove(rightGlovePos, baseRadius * rightGloveScale, fighter.gloveColor, fighter.accentColor)
    }
}

private fun DrawScope.drawPlayerGloves(
    state: FightUiState,
    w: Float,
    h: Float,
    breath: Float,
    guardAnim: Float,
    cameraSway: Float,
    shakeX: Float,
    shakeY: Float
) {
    val swayShift = -cameraSway * w * 0.06f
    val idleLeft = Offset(w * 0.24f + swayShift + shakeX, h * 0.84f + breath * h * 0.012f + shakeY)
    val idleRight = Offset(w * 0.76f + swayShift + shakeX, h * 0.84f - breath * h * 0.012f + shakeY)

    val guardLeft = Offset(w * 0.37f + swayShift, h * 0.66f)
    val guardRight = Offset(w * 0.63f + swayShift, h * 0.66f)

    var leftPos = lerpOffset(idleLeft, guardLeft, guardAnim)
    var rightPos = lerpOffset(idleRight, guardRight, guardAnim)
    var leftScale = 1f + guardAnim * 0.08f
    var rightScale = 1f + guardAnim * 0.08f

    val enemyTargetX = w * 0.50f + state.lateralDelta * w * 0.34f
    val enemyTargetY = h * (0.28f + (1f - (1.16f - state.relativeDistance * 0.38f)) * 0.14f)

    if (state.playerAction == PlayerAction.PUNCHING && state.activePunch != null) {
        val punch = state.activePunch
        val thrust = sin(state.playerPunchProgress * PI.toFloat()).coerceIn(0f, 1f)
        when (punch) {
            PunchType.JAB -> {
                val target = Offset(enemyTargetX - w * 0.03f, enemyTargetY)
                leftPos = lerpOffset(idleLeft, target, thrust)
                leftScale = 1f - thrust * 0.34f
            }
            PunchType.CROSS -> {
                val target = Offset(enemyTargetX + w * 0.03f, enemyTargetY)
                rightPos = lerpOffset(idleRight, target, thrust)
                rightScale = 1f - thrust * 0.34f
            }
            PunchType.HOOK -> {
                val target = Offset(enemyTargetX, enemyTargetY + h * 0.02f)
                val arcX = lerpFloat(idleLeft.x, target.x, thrust) - sin(thrust * PI.toFloat()) * w * 0.13f
                val arcY = lerpFloat(idleLeft.y, target.y, thrust)
                leftPos = Offset(arcX, arcY)
                leftScale = 1f - thrust * 0.28f
            }
            PunchType.UPPERCUT -> {
                val target = Offset(enemyTargetX, enemyTargetY + h * 0.01f)
                val midDip = if (state.playerPunchProgress < 0.35f) h * 0.06f else 0f
                rightPos = lerpOffset(idleRight, target, thrust) + Offset(0f, midDip)
                rightScale = 1f - thrust * 0.26f
            }
        }
    }

    val glove = state.equippedGlove
    val playerGloveBase = w * 0.112f
    val trimColor = if (state.counterWindowMs > 0L) ChampionshipGold else glove.trimColor

    if (guardAnim > 0.35f) {
        drawRoundRect(
            color = CobaltCorner.copy(alpha = 0.22f * guardAnim),
            topLeft = Offset(w * 0.26f + swayShift, h * 0.56f),
            size = Size(w * 0.48f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            style = Stroke(width = w * 0.009f)
        )
    }

    // Counter or Championship Gold glove aura
    if (state.counterWindowMs > 0L || glove.id == "champ_gold") {
        val auraColor = if (state.counterWindowMs > 0L) ChampionshipGold else glove.glowColor
        drawCircle(
            color = auraColor.copy(alpha = 0.22f),
            radius = playerGloveBase * 1.32f,
            center = leftPos
        )
        drawCircle(
            color = auraColor.copy(alpha = 0.22f),
            radius = playerGloveBase * 1.32f,
            center = rightPos
        )
    }

    drawBoxingGlove(leftPos, playerGloveBase * leftScale, glove.primaryColor, trimColor, isPlayer = true)
    drawBoxingGlove(rightPos, playerGloveBase * rightScale, glove.primaryColor, trimColor, isPlayer = true)
}

private fun DrawScope.drawBoxingGlove(
    center: Offset,
    radius: Float,
    color: Color,
    trimColor: Color,
    isPlayer: Boolean = false
) {
    val cuffY = if (isPlayer) center.y + radius * 0.65f else center.y + radius * 0.55f
    drawRoundRect(
        color = trimColor.copy(alpha = 0.92f),
        topLeft = Offset(center.x - radius * 0.55f, cuffY),
        size = Size(radius * 1.1f, radius * 0.55f),
        cornerRadius = CornerRadius(radius * 0.25f, radius * 0.25f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 1f), color.copy(alpha = 0.72f)),
            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
            radius = radius * 1.4f
        ),
        radius = radius,
        center = center
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.28f),
        radius = radius * 0.32f,
        center = Offset(center.x - radius * 0.28f, center.y - radius * 0.30f)
    )
    drawCircle(
        color = trimColor.copy(alpha = 0.45f),
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.08f)
    )
}

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun lerpOffset(a: Offset, b: Offset, t: Float): Offset =
    Offset(lerpFloat(a.x, b.x, t), lerpFloat(a.y, b.y, t))

private fun toneColor(tone: CalloutTone): Color = when (tone) {
    CalloutTone.GOLD -> ChampionshipGold
    CalloutTone.CRIMSON -> CrimsonCorner
    CalloutTone.EMERALD -> StaminaEmerald
    CalloutTone.COBALT -> CobaltGlow
    CalloutTone.WHITE -> ChalkWhite
}
