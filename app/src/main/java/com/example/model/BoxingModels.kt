package com.example.model

import androidx.compose.ui.graphics.Color

enum class HairStyle {
    BUZZ, MOHAWK, FADE_WAVES, HEADBAND
}

data class GloveItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val priceCoins: Int,
    val damageMultiplier: Float,
    val speedMultiplier: Float,
    val staminaCostMultiplier: Float,
    val guardAbsorptionBonus: Float,
    val coinBonusMultiplier: Float,
    val primaryColor: Color,
    val trimColor: Color,
    val glowColor: Color
)

object GloveShopCatalog {
    val gloves: List<GloveItem> = listOf(
        GloveItem(
            id = "classic_red",
            name = "Clásicos Rojos",
            subtitle = "Estándar del Gimnasio",
            description = "Guantes reglamentarios equilibrados para todo tipo de combate.",
            priceCoins = 0,
            damageMultiplier = 1.0f,
            speedMultiplier = 1.0f,
            staminaCostMultiplier = 1.0f,
            guardAbsorptionBonus = 0f,
            coinBonusMultiplier = 1.0f,
            primaryColor = Color(0xFFE63946),
            trimColor = Color(0xFFF8FAFC),
            glowColor = Color(0xFFFF5A66)
        ),
        GloveItem(
            id = "cobalt_flash",
            name = "Cobalto Relámpago",
            subtitle = "Velocidad +15% • -12% Energía",
            description = "Cuero ultraligero que acelera tus golpes y reduce el desgaste físico.",
            priceCoins = 150,
            damageMultiplier = 1.04f,
            speedMultiplier = 1.15f,
            staminaCostMultiplier = 0.88f,
            guardAbsorptionBonus = 0.02f,
            coinBonusMultiplier = 1.05f,
            primaryColor = Color(0xFF2563EB),
            trimColor = Color(0xFF93C5FD),
            glowColor = Color(0xFF60A5FA)
        ),
        GloveItem(
            id = "steel_titan",
            name = "Acero Titán",
            subtitle = "Guardia +25% • Parry Amplio",
            description = "Acolchado de alta densidad para absorber castigo y facilitar contraataques.",
            priceCoins = 280,
            damageMultiplier = 1.08f,
            speedMultiplier = 0.98f,
            staminaCostMultiplier = 0.95f,
            guardAbsorptionBonus = 0.10f,
            coinBonusMultiplier = 1.10f,
            primaryColor = Color(0xFF475569),
            trimColor = Color(0xFF34D399),
            glowColor = Color(0xFF10B981)
        ),
        GloveItem(
            id = "crimson_fury",
            name = "Furia Carmesí",
            subtitle = "Potencia +22% • Rompe Guardias",
            description = "Núcleo compacto de alto impacto pensado para noquear en los rounds finales.",
            priceCoins = 420,
            damageMultiplier = 1.22f,
            speedMultiplier = 1.05f,
            staminaCostMultiplier = 1.04f,
            guardAbsorptionBonus = 0.03f,
            coinBonusMultiplier = 1.15f,
            primaryColor = Color(0xFF991B1B),
            trimColor = Color(0xFFFFB800),
            glowColor = Color(0xFFF59E0B)
        ),
        GloveItem(
            id = "champ_gold",
            name = "Oro de Campeón",
            subtitle = "Potencia +20% • Vel +15% • +25% Monedas",
            description = "Edición legendaria de campeonato con aura dorada y bolsa extra en cada pelea.",
            priceCoins = 650,
            damageMultiplier = 1.20f,
            speedMultiplier = 1.15f,
            staminaCostMultiplier = 0.85f,
            guardAbsorptionBonus = 0.08f,
            coinBonusMultiplier = 1.25f,
            primaryColor = Color(0xFFFFB800),
            trimColor = Color(0xFF18181B),
            glowColor = Color(0xFFFFD147)
        )
    )

    fun byId(id: String): GloveItem = gloves.firstOrNull { it.id == id } ?: gloves.first()
}

data class PlayerEconomy(
    val coins: Int = 200,
    val ownedGloveIds: Set<String> = setOf("classic_red"),
    val equippedGloveId: String = "classic_red"
) {
    val equippedGlove: GloveItem get() = GloveShopCatalog.byId(equippedGloveId)
}

data class FighterProfile(
    val id: String,
    val name: String,
    val nickname: String,
    val weightClass: String,
    val difficultyLabel: String,
    val speedStat: Int,
    val powerStat: Int,
    val guardStat: Int,
    val styleDescription: String,
    val introLine: String,
    val rewardCoins: Int,
    val maxHp: Float,
    val telegraphMs: Long,
    val aiIntervalMinMs: Long,
    val aiIntervalMaxMs: Long,
    val counterChance: Float,
    val guardChance: Float,
    val trunksColor: Color,
    val gloveColor: Color,
    val accentColor: Color,
    val skinTone: Color,
    val hairColor: Color,
    val hairStyle: HairStyle
)

object FighterRoster {
    val fighters: List<FighterProfile> = listOf(
        FighterProfile(
            id = "mateo",
            name = "Mateo Farina",
            nickname = "El Rulos",
            weightClass = "Peso Ligero • 61 kg",
            difficultyLabel = "DIFÍCIL",
            speedStat = 9,
            powerStat = 8,
            guardStat = 7,
            styleDescription = "Ráfagas veloces, fintas constantes y contraataques peligrosos. Nivel difícil desde el primer asalto.",
            introLine = "¡Veloz, implacable y listo para la guerra en la esquina azul!",
            rewardCoins = 320,
            maxHp = 165f,
            telegraphMs = 520L,
            aiIntervalMinMs = 880L,
            aiIntervalMaxMs = 1480L,
            counterChance = 0.34f,
            guardChance = 0.42f,
            trunksColor = Color(0xFF1D4ED8),
            gloveColor = Color(0xFF3B82F6),
            accentColor = Color(0xFF60A5FA),
            skinTone = Color(0xFFD69E76),
            hairColor = Color(0xFF1E1B18),
            hairStyle = HairStyle.FADE_WAVES
        ),
        FighterProfile(
            id = "bruno",
            name = "Benja Rodríguez",
            nickname = "El Black",
            weightClass = "Peso Wélter • 67 kg",
            difficultyLabel = "CAMPEÓN",
            speedStat = 8,
            powerStat = 10,
            guardStat = 8,
            styleDescription = "Campeón implacable de guantes negros. Encadena ganchos devastadores con ritmo de campeonato.",
            introLine = "¡El campeón de los guantes negros entra al ring por el título!",
            rewardCoins = 460,
            maxHp = 195f,
            telegraphMs = 470L,
            aiIntervalMinMs = 800L,
            aiIntervalMaxMs = 1360L,
            counterChance = 0.42f,
            guardChance = 0.46f,
            trunksColor = Color(0xFF18181B),
            gloveColor = Color(0xFF141418),
            accentColor = Color(0xFFD4D4D8),
            skinTone = Color(0xFFC48660),
            hairColor = Color(0xFF141414),
            hairStyle = HairStyle.BUZZ
        ),
        FighterProfile(
            id = "viktor",
            name = "Thiago Lucero",
            nickname = "La Muralla",
            weightClass = "Peso Medio • 73 kg",
            difficultyLabel = "DIFÍCIL",
            speedStat = 7,
            powerStat = 8,
            guardStat = 9,
            styleDescription = "Guardia cerrada y presión constante. Entra al cuerpo a cuerpo con ganchos.",
            introLine = "¡Un muro impenetrable listo para la batalla!",
            rewardCoins = 320,
            maxHp = 165f,
            telegraphMs = 540L,
            aiIntervalMinMs = 940L,
            aiIntervalMaxMs = 1550L,
            counterChance = 0.34f,
            guardChance = 0.44f,
            trunksColor = Color(0xFF047857),
            gloveColor = Color(0xFF10B981),
            accentColor = Color(0xFF34D399),
            skinTone = Color(0xFFE7BA99),
            hairColor = Color(0xFF4A3728),
            hairStyle = HairStyle.MOHAWK
        ),
        FighterProfile(
            id = "brandon",
            name = "Brandon",
            nickname = "La gran muralla",
            weightClass = "Peso Pesado • 94 kg",
            difficultyLabel = "TITÁN",
            speedStat = 6,
            powerStat = 10,
            guardStat = 10,
            styleDescription = "Coloso de defensa impenetrable y manos de piedra. Rompe su guardia con ganchos y esquivas.",
            introLine = "¡Nadie atraviesa a Brandon, La gran muralla!",
            rewardCoins = 420,
            maxHp = 220f,
            telegraphMs = 520L,
            aiIntervalMinMs = 880L,
            aiIntervalMaxMs = 1480L,
            counterChance = 0.38f,
            guardChance = 0.56f,
            trunksColor = Color(0xFF4C1D95),
            gloveColor = Color(0xFF7C3AED),
            accentColor = Color(0xFFA78BFA),
            skinTone = Color(0xFFC68B63),
            hairColor = Color(0xFF171717),
            hairStyle = HairStyle.BUZZ
        ),
        FighterProfile(
            id = "diego",
            name = "Bautista Espinoza",
            nickname = "El Flaquito",
            weightClass = "Leyenda Absoluta • 79 kg",
            difficultyLabel = "LEYENDA",
            speedStat = 10,
            powerStat = 10,
            guardStat = 10,
            styleDescription = "Leyenda invicta del ring. Velocidad máxima, reflejos letales y combos imparables durante los 10 rounds.",
            introLine = "¡La leyenda indiscutida sube al ring a defender su trono!",
            rewardCoins = 600,
            maxHp = 230f,
            telegraphMs = 410L,
            aiIntervalMinMs = 690L,
            aiIntervalMaxMs = 1220L,
            counterChance = 0.50f,
            guardChance = 0.54f,
            trunksColor = Color(0xFF18181B),
            gloveColor = Color(0xFFFFB800),
            accentColor = Color(0xFFFFD147),
            skinTone = Color(0xFFBC7B52),
            hairColor = Color(0xFF0F0F12),
            hairStyle = HairStyle.HEADBAND
        )
    )

    fun byId(id: String): FighterProfile = fighters.firstOrNull { it.id == id } ?: fighters.first()
}

enum class HandSide { LEFT, RIGHT }

enum class PunchType(
    val label: String,
    val side: HandSide,
    val damage: Float,
    val staminaCost: Float,
    val durationMs: Long,
    val breaksGuardFactor: Float,
    val maxRange: Float
) {
    JAB("JAB", HandSide.LEFT, 9f, 8f, 215L, 0.25f, 0.78f),
    CROSS("RECTO", HandSide.RIGHT, 14f, 13f, 275L, 0.35f, 0.80f),
    HOOK("GANCHO", HandSide.LEFT, 19f, 19f, 340L, 0.60f, 0.65f),
    UPPERCUT("UPPERCUT", HandSide.RIGHT, 24f, 24f, 380L, 0.48f, 0.62f)
}

enum class EnemyAttackType(
    val label: String,
    val expectedDodge: HandSide,
    val damage: Float,
    val isHeavy: Boolean,
    val maxRange: Float
) {
    LEFT_JAB("JAB IZQ", HandSide.RIGHT, 11f, false, 0.78f),
    RIGHT_CROSS("RECTO DER", HandSide.LEFT, 16f, true, 0.80f),
    LEFT_HOOK("GANCHO IZQ", HandSide.RIGHT, 21f, true, 0.66f),
    UPPERCUT("UPPERCUT", HandSide.LEFT, 24f, true, 0.62f)
}

enum class DodgeDirection { NONE, LEFT, RIGHT }

enum class PlayerAction {
    IDLE,
    GUARD,
    DODGE_LEFT,
    DODGE_RIGHT,
    PUNCHING,
    HIT_STUN,
    KNOCKED_DOWN
}

enum class EnemyPhase {
    IDLE,
    GUARDING,
    TELEGRAPH,
    STRIKING,
    VULNERABLE,
    HIT_STUN,
    KNOCKED_DOWN
}

enum class FightPhase {
    PRESENTATION,
    INTRO,
    FIGHTING,
    KNOCKDOWN_COUNT,
    ROUND_OVER,
    PAUSED,
    MATCH_OVER
}

enum class MatchOutcome {
    KO_WIN,
    DECISION_WIN,
    KO_LOSS,
    DECISION_LOSS
}

data class MatchStats(
    val punchesThrown: Int = 0,
    val punchesLanded: Int = 0,
    val countersLanded: Int = 0,
    val blocksMade: Int = 0,
    val dodgesMade: Int = 0,
    val knockdownsScored: Int = 0,
    val knockdownsSuffered: Int = 0,
    val elapsedSeconds: Int = 0
) {
    val accuracyPercent: Int
        get() = if (punchesThrown == 0) 0 else ((punchesLanded.toFloat() / punchesThrown) * 100f).toInt()
}

enum class CalloutTone { GOLD, CRIMSON, EMERALD, COBALT, WHITE }

data class FloatingCallout(
    val id: Long,
    val text: String,
    val tone: CalloutTone,
    val xNorm: Float,
    val yNorm: Float,
    val ageMs: Float = 0f,
    val durationMs: Float = 750f
)

data class ImpactSpark(
    val id: Long,
    val xNorm: Float,
    val yNorm: Float,
    val angleRad: Float,
    val speed: Float,
    val tone: CalloutTone,
    val ageMs: Float = 0f,
    val durationMs: Float = 380f
)

data class FightUiState(
    val playerName: String = "Retador",
    val fighter: FighterProfile = FighterRoster.fighters.first(),
    val equippedGlove: GloveItem = GloveShopCatalog.gloves.first(),
    val phase: FightPhase = FightPhase.PRESENTATION,
    val previousPhaseBeforePause: FightPhase = FightPhase.FIGHTING,
    val presentationStep: Int = 0,
    val presentationTitle: String = "",
    val presentationSubtitle: String = "",
    val round: Int = 1,
    val maxRounds: Int = 10,
    val roundTimeMs: Long = 45_000L,
    val roundDifficultyLabel: String = "INICIO",
    val introText: String = "ROUND 1",
    val playerHp: Float = 100f,
    val playerMaxHp: Float = 100f,
    val playerStamina: Float = 100f,
    val playerMaxStamina: Float = 100f,
    val playerPosX: Float = 0f,   // -1f (left corner) .. +1f (right corner)
    val playerPosZ: Float = 0.45f, // 0f (close pocket) .. 1f (outside/far)
    val enemyPosX: Float = 0f,
    val enemyPosZ: Float = 0.08f, // slight enemy depth offset
    val playerAction: PlayerAction = PlayerAction.IDLE,
    val activePunch: PunchType? = null,
    val playerPunchProgress: Float = 0f,
    val dodgeDirection: DodgeDirection = DodgeDirection.NONE,
    val isGuarding: Boolean = false,
    val counterWindowMs: Long = 0L,
    val comboCount: Int = 0,
    val playerRoundPoints: Int = 0,
    val enemyRoundPoints: Int = 0,
    val enemyHp: Float = 115f,
    val enemyMaxHp: Float = 115f,
    val enemyPhase: EnemyPhase = EnemyPhase.IDLE,
    val enemyAttack: EnemyAttackType? = null,
    val enemyPhaseProgress: Float = 0f,
    val enemyHitFlash: Float = 0f,
    val playerHitFlash: Float = 0f,
    val screenShake: Float = 0f,
    val crowdExcitement: Float = 0.25f,
    val crowdBannerText: String = "",
    val knockedDownWho: HandSide? = null,
    val knockdownCount: Int = 0,
    val playerGetUpProgress: Float = 0f,
    val callouts: List<FloatingCallout> = emptyList(),
    val sparks: List<ImpactSpark> = emptyList(),
    val stats: MatchStats = MatchStats(),
    val outcome: MatchOutcome? = null,
    val coinsEarned: Int = 0,
    val roundSummaryText: String = ""
) {
    val relativeDistance: Float
        get() = (playerPosZ - enemyPosZ).coerceIn(0f, 1f)

    val lateralDelta: Float
        get() = enemyPosX - playerPosX
}

data class SessionRecord(
    val wins: Int = 0,
    val kos: Int = 0,
    val currentStreak: Int = 0,
    val defeatedIds: Set<String> = emptySet()
)

data class BoxerSkinOption(
    val name: String,
    val color: Color
)

data class BoxerSkinPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val skinToneIndex: Int,
    val hairColorIndex: Int,
    val trunksColorIndex: Int
)

data class BoxerAppearance(
    val presetId: String = "arcade_classic",
    val skinToneIndex: Int = 1,
    val hairColorIndex: Int = 1,
    val trunksColorIndex: Int = 0
) {
    fun resolvedSkinColor(): Color =
        BoxerSkinCatalog.skinTones[skinToneIndex.coerceIn(0, BoxerSkinCatalog.skinTones.lastIndex)].color

    fun resolvedHairColor(): Color =
        BoxerSkinCatalog.hairColors[hairColorIndex.coerceIn(0, BoxerSkinCatalog.hairColors.lastIndex)].color

    fun resolvedTrunksColor(): Color =
        BoxerSkinCatalog.trunksColors[trunksColorIndex.coerceIn(0, BoxerSkinCatalog.trunksColors.lastIndex)].color
}

object BoxerSkinCatalog {
    val skinTones: List<BoxerSkinOption> = listOf(
        BoxerSkinOption("Claro", Color(0xFFEAC09E)),
        BoxerSkinOption("Trigueño", Color(0xFFC47A4C)),
        BoxerSkinOption("Bronceado", Color(0xFFD69E76)),
        BoxerSkinOption("Canela", Color(0xFF9F5E38)),
        BoxerSkinOption("Oscuro", Color(0xFF693B21))
    )

    val hairColors: List<BoxerSkinOption> = listOf(
        BoxerSkinOption("Negro", Color(0xFF141418)),
        BoxerSkinOption("Castaño", Color(0xFF4A2C1D)),
        BoxerSkinOption("Rubio", Color(0xFFE2B44B)),
        BoxerSkinOption("Platino", Color(0xFFDCE2EE)),
        BoxerSkinOption("Rojo", Color(0xFFC83232)),
        BoxerSkinOption("Azul Neón", Color(0xFF2563EB))
    )

    val trunksColors: List<BoxerSkinOption> = listOf(
        BoxerSkinOption("Rojo Arena", Color(0xFFD62828)),
        BoxerSkinOption("Negro", Color(0xFF1E1E24)),
        BoxerSkinOption("Azul Rey", Color(0xFF1D4ED8)),
        BoxerSkinOption("Oro Leyenda", Color(0xFFD99B00)),
        BoxerSkinOption("Esmeralda", Color(0xFF047857)),
        BoxerSkinOption("Púrpura", Color(0xFF6D28D9))
    )

    val presets: List<BoxerSkinPreset> = listOf(
        BoxerSkinPreset(
            id = "arcade_classic",
            name = "Arcade Clásico",
            subtitle = "Estilo original del ring",
            skinToneIndex = 1,
            hairColorIndex = 1,
            trunksColorIndex = 0
        ),
        BoxerSkinPreset(
            id = "el_black",
            name = "Sombra Negra",
            subtitle = "Cinturón oscuro de campeón",
            skinToneIndex = 3,
            hairColorIndex = 0,
            trunksColorIndex = 1
        ),
        BoxerSkinPreset(
            id = "relampago_azul",
            name = "Relámpago Azul",
            subtitle = "Esquina azul eléctrica",
            skinToneIndex = 2,
            hairColorIndex = 0,
            trunksColorIndex = 2
        ),
        BoxerSkinPreset(
            id = "leyenda_dorada",
            name = "Leyenda Dorada",
            subtitle = "Aura dorada de campeón",
            skinToneIndex = 0,
            hairColorIndex = 2,
            trunksColorIndex = 3
        ),
        BoxerSkinPreset(
            id = "furia_esmeralda",
            name = "Furia Verde",
            subtitle = "Estilo rápido esmeralda",
            skinToneIndex = 2,
            hairColorIndex = 1,
            trunksColorIndex = 4
        ),
        BoxerSkinPreset(
            id = "titan_purpura",
            name = "Titán Púrpura",
            subtitle = "Platino y púrpura real",
            skinToneIndex = 4,
            hairColorIndex = 3,
            trunksColorIndex = 5
        )
    )
}

