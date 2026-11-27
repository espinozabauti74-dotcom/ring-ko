package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RingKoDarkColorScheme = darkColorScheme(
    primary = ChampionshipGold,
    onPrimary = ArenaObsidian,
    primaryContainer = ChampionshipGoldDark,
    onPrimaryContainer = ChalkWhite,
    secondary = CrimsonCorner,
    onSecondary = ChalkWhite,
    secondaryContainer = ArenaCardElevated,
    onSecondaryContainer = ChalkWhite,
    tertiary = CobaltCorner,
    onTertiary = ChalkWhite,
    background = ArenaObsidian,
    onBackground = ChalkWhite,
    surface = ArenaSlate,
    onSurface = ChalkWhite,
    surfaceVariant = ArenaCard,
    onSurfaceVariant = MutedSteel,
    outline = ArenaBorder,
    error = DangerRed,
    onError = ChalkWhite
)

@Composable
fun RingKoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RingKoDarkColorScheme,
        typography = Typography,
        content = content
    )
}
