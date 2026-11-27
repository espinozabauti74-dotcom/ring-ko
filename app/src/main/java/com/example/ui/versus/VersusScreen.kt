package com.example.ui.versus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.VersusBoxerState
import com.example.game.VersusUiState
import com.example.model.CalloutTone
import com.example.model.FightPhase
import com.example.model.PunchType
import com.example.ui.theme.ArenaBorder
import com.example.ui.theme.ArenaCard
import com.example.ui.theme.ArenaCardElevated
import com.example.ui.theme.ArenaObsidian
import com.example.ui.theme.ArenaSlate
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CobaltCorner
import com.example.ui.theme.CobaltGlow
import com.example.ui.theme.CrimsonCorner
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.StaminaEmerald
import com.example.ui.theme.WarningAmber
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun VersusScreen(
    state: VersusUiState,
    onMove: (Int, Float) -> Unit,
    onSetGuard: (Int, Boolean) -> Unit,
    onDodge: (Int) -> Unit,
    onThrowPunch: (Int, PunchType) -> Unit,
    onSkipPresentation: () -> Unit,
    onTogglePause: () -> Unit,
    onRematch: () -> Unit,
    onExit: () -> Unit
) {
    BackHandler {
        if (state.phase == FightPhase.MATCH_OVER) onExit() else onTogglePause()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArenaObsidian)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Half: Player 2 Controls (Rotated 180 degrees for face-to-face play)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ArenaSlate)
                    .graphicsLayer { rotationZ = 180f }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                PlayerPadAndHud(
                    boxer = state.p2,
                    accent = CobaltGlow,
                    onMove = { onMove(2, it) },
                    onSetGuard = { onSetGuard(2, it) },
                    onDodge = { onDodge(2) },
                    onPunch = { onThrowPunch(2, it) }
                )
            }

            // Center Ring Arena
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                VersusRingCanvas(
                    state = state,
                    modifier = Modifier.fillMaxSize()
                )

                // Center Round & Clock Pill + Pause
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    Surface(
                        color = ArenaCard.copy(alpha = 0.90f),
                        border = BorderStroke(1.dp, ChampionshipGold.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        val secs = (state.roundTimeMs / 1000L).toInt().coerceAtLeast(0)
                        Text(
                            text = "R${state.round}/${state.maxRounds} • ${String.format("%02d:%02d", secs / 60, secs % 60)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ChampionshipGold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    IconButton(
                        onClick = onTogglePause,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ArenaCard.copy(alpha = 0.85f))
                            .testTag("versus_pause_button")
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = "Pausa", tint = ChalkWhite)
                    }
                }
            }

            // Bottom Half: Player 1 Controls (Normal orientation)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ArenaSlate)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                PlayerPadAndHud(
                    boxer = state.p1,
                    accent = CrimsonCorner,
                    onMove = { onMove(1, it) },
                    onSetGuard = { onSetGuard(1, it) },
                    onDodge = { onDodge(1) },
                    onPunch = { onThrowPunch(1, it) }
                )
            }
        }

        // Presentation Overlay
        if (state.phase == FightPhase.PRESENTATION) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ArenaObsidian.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ArenaCard),
                    border = BorderStroke(2.dp, ChampionshipGold),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 420.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = state.presentationTitle,
                            style = MaterialTheme.typography.headlineLarge,
                            color = ChampionshipGold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.presentationSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChalkWhite,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onSkipPresentation,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChampionshipGold,
                                contentColor = ArenaObsidian
                            ),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("¡EMPEZAR YA!", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Intro / Round Over Banner
        if ((state.phase == FightPhase.INTRO || state.phase == FightPhase.ROUND_OVER) && state.introText.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = ArenaCard.copy(alpha = 0.94f),
                    border = BorderStroke(2.dp, ChampionshipGold),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = state.introText,
                        style = MaterialTheme.typography.displayMedium,
                        color = ChampionshipGold,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp)
                    )
                }
            }
        }

        // Pause Overlay
        if (state.phase == FightPhase.PAUSED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ArenaObsidian.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ArenaCard),
                    border = BorderStroke(1.5.dp, ArenaBorder),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("DUELO EN PAUSA", style = MaterialTheme.typography.displayMedium, color = ChampionshipGold)
                        Button(
                            onClick = onTogglePause,
                            colors = ButtonDefaults.buttonColors(containerColor = ChampionshipGold, contentColor = ArenaObsidian),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("REANUDAR", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = onExit,
                            border = BorderStroke(1.dp, CrimsonCorner),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("SALIR AL MENÚ", color = CrimsonCorner, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Match Result Overlay
        if (state.phase == FightPhase.MATCH_OVER) {
            val winnerName = if (state.winnerIndex == 1) state.p1.name else state.p2.name
            val winnerColor = if (state.winnerIndex == 1) CrimsonCorner else CobaltGlow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ArenaObsidian.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ArenaCard),
                    border = BorderStroke(2.dp, winnerColor),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .padding(20.dp)
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = ChampionshipGold, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "¡GANADOR: ${winnerName.uppercase()}!",
                            style = MaterialTheme.typography.headlineLarge,
                            color = winnerColor,
                            textAlign = TextAlign.Center
                        )
                        Text(text = state.winMethod, style = MaterialTheme.typography.titleMedium, color = ChampionshipGold)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "${state.p1.name}: ${state.p1.punchesLanded} golpes • ${state.p2.name}: ${state.p2.punchesLanded} golpes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = onRematch,
                            colors = ButtonDefaults.buttonColors(containerColor = ChampionshipGold, contentColor = ArenaObsidian),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("REVANCHA", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onExit,
                            border = BorderStroke(1.dp, ArenaBorder),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("VOLVER AL MENÚ", color = ChalkWhite, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPadAndHud(
    boxer: VersusBoxerState,
    accent: Color,
    onMove: (Float) -> Unit,
    onSetGuard: (Boolean) -> Unit,
    onDodge: () -> Unit,
    onPunch: (PunchType) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
    ) {
        // Player Status Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = boxer.name.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp)
            )
            LinearProgressIndicator(
                progress = { (boxer.hp / boxer.maxHp).coerceIn(0f, 1f) },
                color = if (boxer.hp < 35f) CrimsonCorner else StaminaEmerald,
                trackColor = ArenaCard,
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            )
            LinearProgressIndicator(
                progress = { (boxer.stamina / boxer.maxStamina).coerceIn(0f, 1f) },
                color = CobaltGlow,
                trackColor = ArenaCard,
                modifier = Modifier
                    .width(68.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            if (boxer.counterWindowMs > 0L) {
                Surface(color = ChampionshipGold, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "2x",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ArenaObsidian,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Controls Row 1: Move Left | Move Right | Dodge | Guard
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            VersusHoldBtn("◄", MutedSteel, { onMove(if (it) -1f else 0f) }, Modifier.weight(1f).height(48.dp))
            VersusHoldBtn("►", MutedSteel, { onMove(if (it) 1f else 0f) }, Modifier.weight(1f).height(48.dp))
            VersusTapBtn("ESQUIVA", StaminaEmerald, boxer.isDodging, onDodge, Modifier.weight(1.2f).height(48.dp))
            VersusHoldBtn("GUARDIA", CobaltGlow, onSetGuard, Modifier.weight(1.3f).height(48.dp), showShield = true)
        }

        // Controls Row 2: Jab | Gancho | Uppercut
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            VersusTapBtn("JAB", ChalkWhite, boxer.activePunch == PunchType.JAB, { onPunch(PunchType.JAB) }, Modifier.weight(1f).height(48.dp))
            VersusTapBtn("GANCHO", WarningAmber, boxer.activePunch == PunchType.HOOK, { onPunch(PunchType.HOOK) }, Modifier.weight(1f).height(48.dp))
            VersusTapBtn("UPPERCUT", ChampionshipGold, boxer.activePunch == PunchType.UPPERCUT, { onPunch(PunchType.UPPERCUT) }, Modifier.weight(1f).height(48.dp))
        }
    }
}

@Composable
private fun VersusHoldBtn(
    label: String,
    accent: Color,
    onHeldChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showShield: Boolean = false
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) accent.copy(alpha = 0.30f) else ArenaCard)
            .border(1.dp, if (pressed) accent else ArenaBorder, RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onHeldChange(true)
                    waitForUpOrCancellation()
                    pressed = false
                    onHeldChange(false)
                }
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showShield) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = ChalkWhite)
        }
    }
}

@Composable
private fun VersusTapBtn(
    label: String,
    accent: Color,
    active: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed || active) accent.copy(alpha = 0.28f) else ArenaCardElevated)
            .border(if (pressed || active) 1.5.dp else 1.dp, if (pressed || active) accent else ArenaBorder, RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onPress()
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = ChalkWhite)
    }
}

@Composable
private fun VersusRingCanvas(
    state: VersusUiState,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "versus_anim")
    val animPhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val shakeX = sin(animPhase * 12f) * state.screenShake * w * 0.018f

            // Backdrop & animated crowd
            drawRect(
                brush = Brush.verticalGradient(listOf(ArenaObsidian, Color(0xFF151B2C), ArenaSlate))
            )

            for (i in 0..16) {
                val cx = (i / 16f) * w
                val bounce = abs(sin(animPhase * (2f + state.crowdExcitement * 4f) + i)) * h * (0.01f + state.crowdExcitement * 0.04f)
                val cy = h * 0.36f - bounce
                val col = when (i % 3) {
                    0 -> CrimsonCorner.copy(alpha = 0.65f)
                    1 -> CobaltCorner.copy(alpha = 0.65f)
                    else -> ChampionshipGold.copy(alpha = 0.60f)
                }
                drawCircle(color = col, radius = w * 0.018f, center = Offset(cx, cy))
                drawRoundRect(
                    color = col,
                    topLeft = Offset(cx - w * 0.022f, cy + w * 0.014f),
                    size = Size(w * 0.044f, h * 0.14f),
                    cornerRadius = CornerRadius(w * 0.015f, w * 0.015f)
                )
            }

            // Ring Mat & Ropes
            val matTop = h * 0.56f
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF20283A), ArenaObsidian)),
                topLeft = Offset(0f, matTop),
                size = Size(w, h - matTop)
            )
            drawLine(CrimsonCorner.copy(alpha = 0.7f), Offset(0f, h * 0.32f), Offset(w, h * 0.32f), strokeWidth = 5f)
            drawLine(ChalkWhite.copy(alpha = 0.65f), Offset(0f, h * 0.43f), Offset(w, h * 0.43f), strokeWidth = 5f)
            drawLine(CobaltCorner.copy(alpha = 0.7f), Offset(0f, h * 0.54f), Offset(w, h * 0.54f), strokeWidth = 5f)

            // Draw Player 1 (Left facing Right) & Player 2 (Right facing Left)
            drawSideBoxer(
                boxer = state.p1,
                facingRight = true,
                w = w,
                h = h,
                shakeX = shakeX,
                breath = sin(animPhase),
                trunksColor = CrimsonCorner,
                gloveColor = CrimsonCorner
            )
            drawSideBoxer(
                boxer = state.p2,
                facingRight = false,
                w = w,
                h = h,
                shakeX = shakeX,
                breath = -sin(animPhase),
                trunksColor = CobaltCorner,
                gloveColor = CobaltGlow
            )

            // Sparks
            state.sparks.forEach { spark ->
                val t = (spark.ageMs / spark.durationMs).coerceIn(0f, 1f)
                val dist = spark.speed * w * t
                val cx = spark.xNorm * w + cos(spark.angleRad) * dist
                val cy = spark.yNorm * h + sin(spark.angleRad) * dist
                val col = when (spark.tone) {
                    CalloutTone.GOLD -> ChampionshipGold
                    CalloutTone.CRIMSON -> CrimsonCorner
                    CalloutTone.EMERALD -> StaminaEmerald
                    CalloutTone.COBALT -> CobaltGlow
                    CalloutTone.WHITE -> ChalkWhite
                }.copy(alpha = 1f - t)
                drawCircle(color = col, radius = w * 0.012f * (1f - t * 0.5f), center = Offset(cx, cy))
            }
        }

        // Callouts
        Layout(
            content = {
                state.callouts.forEach { c ->
                    val prog = (c.ageMs / c.durationMs).coerceIn(0f, 1f)
                    val col = when (c.tone) {
                        CalloutTone.GOLD -> ChampionshipGold
                        CalloutTone.CRIMSON -> CrimsonCorner
                        CalloutTone.EMERALD -> StaminaEmerald
                        CalloutTone.COBALT -> CobaltGlow
                        CalloutTone.WHITE -> ChalkWhite
                    }.copy(alpha = (1f - prog * prog).coerceIn(0f, 1f))
                    Text(
                        text = c.text,
                        style = MaterialTheme.typography.headlineMedium,
                        color = col
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { idx, p ->
                    val c = state.callouts.getOrNull(idx) ?: return@forEachIndexed
                    val prog = (c.ageMs / c.durationMs).coerceIn(0f, 1f)
                    val x = (c.xNorm * constraints.maxWidth - p.width / 2f).roundToInt()
                    val y = (c.yNorm * constraints.maxHeight - p.height / 2f - prog * constraints.maxHeight * 0.1f).roundToInt()
                    p.placeRelative(IntOffset(x, y))
                }
            }
        }
    }
}

private fun DrawScope.drawSideBoxer(
    boxer: VersusBoxerState,
    facingRight: Boolean,
    w: Float,
    h: Float,
    shakeX: Float,
    breath: Float,
    trunksColor: Color,
    gloveColor: Color
) {
    val dir = if (facingRight) 1f else -1f
    val dodgeShift = if (boxer.isDodging) -dir * w * 0.04f else 0f
    val cx = w * 0.50f + boxer.posX * w * 0.42f + dodgeShift + shakeX
    val baseFootY = h * 0.86f
    val dropY = if (boxer.isKnockedDown) h * 0.25f else 0f
    val bobY = abs(breath) * h * 0.015f

    val headR = h * 0.105f
    val bodyH = h * 0.25f
    val bodyW = w * 0.085f
    val hipY = baseFootY - h * 0.20f + dropY
    val shoulderY = hipY - bodyH + bobY
    val headY = shoulderY - headR * 1.1f

    // Legs
    drawLine(Color(0xFFD69E76), Offset(cx - dir * bodyW * 0.25f, hipY), Offset(cx - dir * bodyW * 0.45f, baseFootY), strokeWidth = w * 0.025f, cap = StrokeCap.Round)
    drawLine(Color(0xFFD69E76), Offset(cx + dir * bodyW * 0.25f, hipY), Offset(cx + dir * bodyW * 0.45f, baseFootY), strokeWidth = w * 0.025f, cap = StrokeCap.Round)

    // Trunks
    drawRoundRect(
        color = trunksColor,
        topLeft = Offset(cx - bodyW * 0.55f, hipY - h * 0.06f),
        size = Size(bodyW * 1.1f, h * 0.12f),
        cornerRadius = CornerRadius(12f, 12f)
    )

    // Torso
    drawRoundRect(
        color = if (boxer.hitFlash > 0.3f) Color.White else Color(0xFFD69E76),
        topLeft = Offset(cx - bodyW * 0.52f, shoulderY),
        size = Size(bodyW * 1.04f, bodyH),
        cornerRadius = CornerRadius(16f, 16f)
    )

    // Head
    drawCircle(
        color = if (boxer.hitFlash > 0.3f) Color.White else Color(0xFFD69E76),
        radius = headR,
        center = Offset(cx + dir * bodyW * 0.06f, headY)
    )
    // Headband
    drawRect(
        color = trunksColor,
        topLeft = Offset(cx - headR * 0.85f, headY - headR * 0.55f),
        size = Size(headR * 1.7f, headR * 0.35f)
    )

    // Glove arm extension
    val thrust = if (boxer.activePunch != null) sin(boxer.punchProgress * PI.toFloat()).coerceIn(0f, 1f) else 0f
    val reachPx = when (boxer.activePunch) {
        PunchType.JAB, PunchType.CROSS -> w * 0.17f
        PunchType.HOOK -> w * 0.14f
        PunchType.UPPERCUT -> w * 0.13f
        null -> 0f
    }
    val guardLift = if (boxer.isGuarding) -headR * 0.35f else 0f
    val punchLift = if (boxer.activePunch == PunchType.UPPERCUT) -headR * 0.55f * thrust else 0f
    val gloveX = cx + dir * (bodyW * 0.72f + reachPx * thrust)
    val gloveY = shoulderY + headR * 0.25f + guardLift + punchLift

    drawLine(
        color = Color(0xFFD69E76),
        start = Offset(cx + dir * bodyW * 0.2f, shoulderY + h * 0.03f),
        end = Offset(gloveX, gloveY),
        strokeWidth = w * 0.022f,
        cap = StrokeCap.Round
    )
    drawCircle(color = gloveColor, radius = h * 0.068f, center = Offset(gloveX, gloveY))
    drawCircle(color = Color.Black.copy(alpha = 0.3f), radius = h * 0.068f, center = Offset(gloveX, gloveY), style = Stroke(3f))

    if (boxer.isGuarding) {
        drawArc(
            color = CobaltGlow.copy(alpha = 0.65f),
            startAngle = if (facingRight) -65f else 115f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(cx - bodyW * 0.9f, headY - headR),
            size = Size(bodyW * 1.8f, bodyH * 0.9f),
            style = Stroke(width = 5f)
        )
    }
}
