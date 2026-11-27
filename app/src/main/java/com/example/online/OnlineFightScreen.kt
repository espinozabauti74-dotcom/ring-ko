package com.example.online

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CalloutTone
import com.example.model.PunchType
import com.example.ui.components.drawPixelArtBoxer
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
import kotlin.math.sin

@Composable
fun OnlineFightScreen(
    state: OnlineFightUiState,
    onMove: (Float) -> Unit,
    onSetGuard: (Boolean) -> Unit,
    onDodge: () -> Unit,
    onThrowPunch: (PunchType) -> Unit,
    onSkipPresentation: () -> Unit,
    onExit: () -> Unit
) {
    BackHandler { onExit() }

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
            // Top HUD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ArenaSlate.copy(alpha = 0.94f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onExit,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ArenaCard)
                            .testTag("online_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Salir",
                            tint = ChalkWhite
                        )
                    }

                    Surface(
                        color = ArenaCard,
                        border = BorderStroke(1.dp, ChampionshipGold.copy(alpha = 0.65f)),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        val secs = (state.roundTimeMs / 1000L).toInt().coerceAtLeast(0)
                        val roomLabel = if (state.isWifiLocal) "WI-FI LOCAL" else "SALA ${state.matchId}"
                        Text(
                            text = "$roomLabel • R${state.currentRound}/${state.maxRounds} • ${String.format("%02d:%02d", secs / 60, secs % 60)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ChampionshipGold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.myName.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CrimsonCorner,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            progress = { (state.myHp / state.myMaxHp).coerceIn(0f, 1f) },
                            color = CrimsonCorner,
                            trackColor = ArenaObsidian,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (state.myStamina / 100f).coerceIn(0f, 1f) },
                            color = StaminaEmerald,
                            trackColor = ArenaObsidian,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = state.oppName.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CobaltGlow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            progress = { (state.oppHp / state.oppMaxHp).coerceIn(0f, 1f) },
                            color = CobaltGlow,
                            trackColor = ArenaObsidian,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (state.oppStamina / 100f).coerceIn(0f, 1f) },
                            color = WarningAmber,
                            trackColor = ArenaObsidian,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            // Ring Arena
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                OnlineRingCanvas(
                    state = state,
                    modifier = Modifier.fillMaxSize()
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = state.calloutText.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                ) {
                    val badgeColor = when (state.calloutTone) {
                        CalloutTone.EMERALD -> StaminaEmerald
                        CalloutTone.GOLD -> ChampionshipGold
                        CalloutTone.CRIMSON -> CrimsonCorner
                        CalloutTone.COBALT -> CobaltGlow
                        CalloutTone.WHITE -> ChalkWhite
                    }
                    Surface(
                        color = ArenaObsidian.copy(alpha = 0.85f),
                        border = BorderStroke(1.5.dp, badgeColor),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = state.calloutText,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Bottom Fighter Controls
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ArenaSlate)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HoldControlButton(
                        label = "◄ MOVER",
                        accent = CobaltGlow,
                        onHoldChange = { held -> onMove(if (held) -1f else 0f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )
                    Button(
                        onClick = onDodge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArenaCardElevated,
                            contentColor = ChampionshipGold
                        ),
                        border = BorderStroke(1.dp, ChampionshipGold.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("ESQUIVAR", style = MaterialTheme.typography.labelMedium)
                    }
                    HoldControlButton(
                        label = "GUARDIA",
                        accent = StaminaEmerald,
                        onHoldChange = onSetGuard,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )
                    HoldControlButton(
                        label = "MOVER ►",
                        accent = CobaltGlow,
                        onHoldChange = { held -> onMove(if (held) 1f else 0f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        PunchType.JAB to "JAB",
                        PunchType.CROSS to "RECTO",
                        PunchType.HOOK to "GANCHO",
                        PunchType.UPPERCUT to "UPPER"
                    ).forEach { (punch, label) ->
                        Button(
                            onClick = { onThrowPunch(punch) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonCorner,
                                contentColor = ChalkWhite
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("online_punch_${punch.name}")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Loading / Waiting / Presentation / Error / Finished Overlays
        when (state.phase) {
            OnlineBoutPhase.LOADING -> {
                OverlayCard {
                    CircularProgressIndicator(color = ChampionshipGold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("CONECTANDO AL RING...", style = MaterialTheme.typography.titleLarge, color = ChalkWhite)
                }
            }

            OnlineBoutPhase.WAITING_OPPONENT -> {
                OverlayCard {
                    Text(
                        text = if (state.isWifiLocal) "ESPERANDO RIVAL EN WI-FI" else "ESPERANDO RIVAL ONLINE",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ChampionshipGold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (state.isWifiLocal) {
                            "El otro celular debe estar en el mismo Wi-Fi o Zona Wi-Fi y tocar tu sala, o ingresar esta dirección:"
                        } else {
                            "Comparte este código para que tu oponente se una a la pelea:"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = ArenaObsidian,
                        border = BorderStroke(2.dp, ChampionshipGold),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        val displayCode = if (state.isWifiLocal && state.wifiAddressHint.isNotBlank()) {
                            state.wifiAddressHint
                        } else {
                            state.matchId
                        }
                        Text(
                            text = displayCode,
                            style = if (state.isWifiLocal) {
                                MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.displayLarge.copy(letterSpacing = 6.sp)
                            },
                            color = ChampionshipGold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    CircularProgressIndicator(color = ChampionshipGold, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onExit,
                        border = BorderStroke(1.dp, CrimsonCorner),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("CANCELAR SALA", color = CrimsonCorner)
                    }
                }
            }

            OnlineBoutPhase.PRESENTATION -> {
                OverlayCard {
                    Text(
                        text = if (state.isWifiLocal) "¡COMBATE WI-FI!" else "¡COMBATE ONLINE!",
                        style = MaterialTheme.typography.headlineLarge,
                        color = ChampionshipGold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${state.myName}  VS  ${state.oppName}",
                        style = MaterialTheme.typography.titleLarge,
                        color = ChalkWhite,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${state.maxRounds} ROUNDS • RING EN VIVO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedSteel
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

            OnlineBoutPhase.FINISHED -> {
                val won = state.winnerIsMe == true
                OverlayCard {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = if (won) ChampionshipGold else MutedSteel,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (won) (if (state.isWifiLocal) "¡VICTORIA WI-FI!" else "¡VICTORIA ONLINE!") else "DERROTA",
                        style = MaterialTheme.typography.displaySmall,
                        color = if (won) ChampionshipGold else CrimsonCorner,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Por ${state.winMethod ?: "KO"} • ${state.myName} vs ${state.oppName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkWhite,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onExit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChampionshipGold,
                            contentColor = ArenaObsidian
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("online_finish_exit_button")
                    ) {
                        Text("VOLVER AL MENÚ", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            OnlineBoutPhase.ERROR -> {
                OverlayCard {
                    Text(
                        text = "AVISO DE SALA",
                        style = MaterialTheme.typography.headlineMedium,
                        color = CrimsonCorner
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.errorMessage ?: "Se perdió la conexión con la sala.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkWhite,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onExit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChampionshipGold,
                            contentColor = ArenaObsidian
                        ),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("VOLVER AL MENÚ", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            OnlineBoutPhase.FIGHTING -> Unit
        }
    }
}

@Composable
private fun OverlayCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArenaObsidian.copy(alpha = 0.72f)),
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
                content()
            }
        }
    }
}

@Composable
private fun HoldControlButton(
    label: String,
    accent: Color,
    onHoldChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    Surface(
        color = if (pressed) accent.copy(alpha = 0.28f) else ArenaCardElevated,
        border = BorderStroke(1.5.dp, if (pressed) accent else ArenaBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                onHoldChange(true)
                waitForUpOrCancellation()
                pressed = false
                onHoldChange(false)
            }
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (pressed) ChalkWhite else accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OnlineRingCanvas(
    state: OnlineFightUiState,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "online_ring_anim")
    val animPhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart),
        label = "breath"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Arena backdrop
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0A0D16), Color(0xFF171E2E), Color(0xFF101522))
            )
        )

        // Crowd silhouettes
        val crowdCols = 18
        for (i in 0 until crowdCols) {
            val cx = (i + 0.5f) * (w / crowdCols)
            val bounce = sin((i * 1.1f + state.crowdExcitement * 6f).toDouble()).toFloat() * 5f
            drawCircle(
                color = Color(0xFF232B3D),
                radius = w * 0.022f,
                center = Offset(cx, h * 0.22f + bounce)
            )
            drawRoundRect(
                color = Color(0xFF1D2434),
                topLeft = Offset(cx - w * 0.028f, h * 0.24f + bounce),
                size = Size(w * 0.056f, h * 0.12f),
                cornerRadius = CornerRadius(10f, 10f)
            )
        }

        // Ring Mat
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1E293B), Color(0xFF151C2B))
            ),
            topLeft = Offset(0f, h * 0.56f),
            size = Size(w, h * 0.44f)
        )

        // Ropes
        listOf(0.36f, 0.44f, 0.52f).forEachIndexed { idx, ratio ->
            val ropeColor = if (idx == 0) CrimsonCorner else if (idx == 2) CobaltCorner else ChalkWhite.copy(alpha = 0.7f)
            drawLine(
                color = ropeColor,
                start = Offset(0f, h * ratio),
                end = Offset(w, h * ratio),
                strokeWidth = 5f
            )
        }

        val spriteH = h * 0.52f
        val myCenterX = w * (0.5f + state.myPosX * 0.40f)
        val oppCenterX = w * (0.5f + state.oppPosX * 0.40f)
        val myFacingRight = state.myPosX <= state.oppPosX
        val oppFacingRight = !myFacingRight

        val myBottomY = h * 0.85f
        val oppBottomY = h * 0.82f
        val myBob = kotlin.math.abs(sin(animPhase)) * h * 0.015f
        val oppBob = kotlin.math.abs(sin(animPhase + 1.4f)) * h * 0.015f

        val resolvedOppGloveColor = state.oppGloveColorOverride ?: state.oppGlove.primaryColor
        val resolvedMyGloveColor = state.myGloveColorOverride ?: state.myGlove.primaryColor

        // Draw Opponent pixel-art boxer
        drawPixelArtBoxer(
            centerX = oppCenterX,
            bottomY = oppBottomY,
            spriteHeight = spriteH,
            facingRight = oppFacingRight,
            skinBase = state.oppSkinColor,
            hairColor = state.oppHairColor,
            trunksColor = state.oppTrunksColor,
            gloveColor = resolvedOppGloveColor,
            isGuarding = state.oppGuarding,
            isDodging = state.oppDodging,
            isKnockedDown = state.oppHp <= 0f,
            activePunch = state.oppActivePunch,
            punchProgress = state.oppPunchProgress,
            hitFlashAlpha = state.oppHitFlashAlpha,
            bobOffsetPx = oppBob
        )
        if (state.oppGuarding) {
            val dir = if (oppFacingRight) 1f else -1f
            drawArc(
                color = CobaltGlow.copy(alpha = 0.65f),
                startAngle = if (oppFacingRight) -65f else 115f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(oppCenterX + dir * spriteH * 0.04f - spriteH * 0.24f, oppBottomY - spriteH * 0.88f),
                size = Size(spriteH * 0.48f, spriteH * 0.46f),
                style = Stroke(width = 5f)
            )
        }

        // Draw Local Player pixel-art boxer (same sprite design, differing in gloves & trunks)
        drawPixelArtBoxer(
            centerX = myCenterX,
            bottomY = myBottomY,
            spriteHeight = spriteH,
            facingRight = myFacingRight,
            skinBase = state.mySkinColor,
            hairColor = state.myHairColor,
            trunksColor = state.myTrunksColor,
            gloveColor = resolvedMyGloveColor,
            isGuarding = state.myGuarding,
            isDodging = state.myDodging,
            isKnockedDown = state.myHp <= 0f,
            activePunch = state.myActivePunch,
            punchProgress = state.myPunchProgress,
            hitFlashAlpha = state.hitFlashAlpha,
            bobOffsetPx = myBob
        )
        if (state.myGuarding) {
            val dir = if (myFacingRight) 1f else -1f
            drawArc(
                color = StaminaEmerald.copy(alpha = 0.7f),
                startAngle = if (myFacingRight) -65f else 115f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(myCenterX + dir * spriteH * 0.04f - spriteH * 0.24f, myBottomY - spriteH * 0.88f),
                size = Size(spriteH * 0.48f, spriteH * 0.46f),
                style = Stroke(width = 5f)
            )
        }

        // Subtle gold pointer arrow above your boxer so you always spot yourself
        val markerY = myBottomY - spriteH - h * 0.03f + myBob
        val arrowPath = Path().apply {
            moveTo(myCenterX, markerY + h * 0.025f)
            lineTo(myCenterX - w * 0.022f, markerY)
            lineTo(myCenterX + w * 0.022f, markerY)
            close()
        }
        drawPath(arrowPath, color = ChampionshipGold)

        if (state.hitFlashAlpha > 0.02f) {
            drawRect(color = CrimsonCorner.copy(alpha = state.hitFlashAlpha * 0.28f))
        }
    }
}
