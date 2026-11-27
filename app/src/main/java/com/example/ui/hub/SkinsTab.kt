package com.example.ui.hub

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BoxerAppearance
import com.example.model.BoxerSkinCatalog
import com.example.model.BoxerSkinOption
import com.example.model.GloveItem
import com.example.ui.components.drawPixelArtBoxer
import com.example.ui.theme.ArenaBorder
import com.example.ui.theme.ArenaCard
import com.example.ui.theme.ArenaCardElevated
import com.example.ui.theme.ArenaObsidian
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CobaltCorner
import com.example.ui.theme.CrimsonCorner
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.StaminaEmerald
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun SkinsTab(
    playerName: String,
    appearance: BoxerAppearance,
    equippedGlove: GloveItem,
    onUpdateAppearance: (BoxerAppearance) -> Unit
) {
    var previewGuard by remember { mutableStateOf(false) }

    val inf = rememberInfiniteTransition(label = "skin_preview_bob")
    val animPhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "bob"
    )

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Live Pixel-Art Preview Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ArenaCard),
                border = BorderStroke(1.5.dp, ChampionshipGold.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = ChampionshipGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "PERSONALIZAR SKIN",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ChampionshipGold
                                )
                            }
                            Text(
                                text = "$playerName • Guantes: ${equippedGlove.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSteel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = { previewGuard = !previewGuard },
                            border = BorderStroke(1.dp, if (previewGuard) StaminaEmerald else ArenaBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (previewGuard) StaminaEmerald.copy(alpha = 0.16f) else ArenaCardElevated,
                                contentColor = if (previewGuard) StaminaEmerald else ChalkWhite
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("skin_preview_guard_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (previewGuard) "GUARDIA" else "POSE",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, ArenaBorder, RoundedCornerShape(14.dp))
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .semantics { contentDescription = "Vista previa de tu boxeador" }
                        ) {
                            val w = size.width
                            val h = size.height

                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF0E1320), Color(0xFF1A2236), Color(0xFF121826))
                                )
                            )
                            // Ring mat & ropes
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF212C40), Color(0xFF151C2B))
                                ),
                                topLeft = Offset(0f, h * 0.66f),
                                size = Size(w, h * 0.34f)
                            )
                            drawLine(CrimsonCorner.copy(alpha = 0.65f), Offset(0f, h * 0.38f), Offset(w, h * 0.38f), strokeWidth = 4f)
                            drawLine(ChalkWhite.copy(alpha = 0.55f), Offset(0f, h * 0.49f), Offset(w, h * 0.49f), strokeWidth = 4f)
                            drawLine(CobaltCorner.copy(alpha = 0.65f), Offset(0f, h * 0.60f), Offset(w, h * 0.60f), strokeWidth = 4f)

                            val bob = abs(sin(animPhase)) * h * 0.025f
                            drawPixelArtBoxer(
                                centerX = w * 0.5f,
                                bottomY = h * 0.90f,
                                spriteHeight = h * 0.72f,
                                facingRight = true,
                                skinBase = appearance.resolvedSkinColor(),
                                hairColor = appearance.resolvedHairColor(),
                                trunksColor = appearance.resolvedTrunksColor(),
                                gloveColor = equippedGlove.primaryColor,
                                isGuarding = previewGuard,
                                bobOffsetPx = bob
                            )
                        }
                    }
                }
            }
        }

        // Quick Presets
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ArenaCard),
                border = BorderStroke(1.dp, ArenaBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "ESTILOS RÁPIDOS",
                        style = MaterialTheme.typography.titleMedium,
                        color = ChalkWhite
                    )
                    val presetRows = BoxerSkinCatalog.presets.chunked(2)
                    presetRows.forEach { rowPresets ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowPresets.forEach { preset ->
                                val selected = appearance.presetId == preset.id &&
                                    appearance.skinToneIndex == preset.skinToneIndex &&
                                    appearance.hairColorIndex == preset.hairColorIndex &&
                                    appearance.trunksColorIndex == preset.trunksColorIndex

                                val skinColor = BoxerSkinCatalog.skinTones[preset.skinToneIndex].color
                                val hairColor = BoxerSkinCatalog.hairColors[preset.hairColorIndex].color
                                val trunksColor = BoxerSkinCatalog.trunksColors[preset.trunksColorIndex].color

                                Surface(
                                    color = if (selected) ArenaCardElevated else ArenaObsidian.copy(alpha = 0.65f),
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) ChampionshipGold else ArenaBorder
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onUpdateAppearance(
                                                BoxerAppearance(
                                                    presetId = preset.id,
                                                    skinToneIndex = preset.skinToneIndex,
                                                    hairColorIndex = preset.hairColorIndex,
                                                    trunksColorIndex = preset.trunksColorIndex
                                                )
                                            )
                                        }
                                        .testTag("skin_preset_${preset.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                            ColorDot(skinColor)
                                            ColorDot(hairColor)
                                            ColorDot(trunksColor)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = preset.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (selected) ChampionshipGold else ChalkWhite,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = preset.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MutedSteel,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Skin Tone Picker
        item {
            ColorSwatchSection(
                title = "TONO DE PIEL",
                options = BoxerSkinCatalog.skinTones,
                selectedIndex = appearance.skinToneIndex,
                tagPrefix = "skin_tone",
                onSelect = { idx ->
                    onUpdateAppearance(appearance.copy(presetId = "custom", skinToneIndex = idx))
                }
            )
        }

        // Hair Color Picker
        item {
            ColorSwatchSection(
                title = "COLOR DE CABELLO",
                options = BoxerSkinCatalog.hairColors,
                selectedIndex = appearance.hairColorIndex,
                tagPrefix = "hair_color",
                onSelect = { idx ->
                    onUpdateAppearance(appearance.copy(presetId = "custom", hairColorIndex = idx))
                }
            )
        }

        // Trunks / Waistband Color Picker
        item {
            ColorSwatchSection(
                title = "PANTALÓN / CINTURÓN",
                options = BoxerSkinCatalog.trunksColors,
                selectedIndex = appearance.trunksColorIndex,
                tagPrefix = "trunks_color",
                onSelect = { idx ->
                    onUpdateAppearance(appearance.copy(presetId = "custom", trunksColorIndex = idx))
                }
            )
        }
    }
}

@Composable
private fun ColorDot(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, ArenaObsidian, CircleShape)
    )
}

@Composable
private fun ColorSwatchSection(
    title: String,
    options: List<BoxerSkinOption>,
    selectedIndex: Int,
    tagPrefix: String,
    onSelect: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ArenaCard),
        border = BorderStroke(1.dp, ArenaBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ChalkWhite
                )
                val activeName = options.getOrNull(selectedIndex)?.name ?: ""
                Text(
                    text = activeName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ChampionshipGold
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(options) { idx, option ->
                    val selected = idx == selectedIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(idx) }
                            .padding(4.dp)
                            .testTag("${tagPrefix}_$idx")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(option.color)
                                .border(
                                    width = if (selected) 3.dp else 1.5.dp,
                                    color = if (selected) ChampionshipGold else ArenaBorder,
                                    shape = CircleShape
                                )
                        ) {
                            if (selected) {
                                val checkTint = if (
                                    option.color.red * 0.299f +
                                    option.color.green * 0.587f +
                                    option.color.blue * 0.114f > 0.6f
                                ) ArenaObsidian else ChalkWhite
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = option.name,
                                    tint = checkTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) ChampionshipGold else MutedSteel
                        )
                    }
                }
            }
        }
    }
}
