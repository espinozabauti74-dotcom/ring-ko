package com.example.chat

import androidx.compose.ui.graphics.toArgb
import com.example.model.BoxerAppearance
import com.example.model.FighterRoster
import com.example.model.GloveShopCatalog
import kotlin.random.Random

data class ChatRoomMember(
    val peerId: String,
    val name: String,
    val characterId: String,
    val gloveId: String,
    val skinArgb: Int,
    val hairArgb: Int,
    val trunksArgb: Int,
    val gloveArgb: Int,
    val trimArgb: Int,
    val lastSeenMs: Long
)

data class ChatRoomMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestampMs: Long,
    val isSystem: Boolean = false
)

data class ChatChallenge(
    val challengeId: String,
    val fromPeerId: String,
    val fromName: String,
    val toPeerId: String?,
    val rounds: Int,
    val characterId: String,
    val gloveId: String,
    val skinArgb: Int,
    val hairArgb: Int,
    val trunksArgb: Int,
    val gloveArgb: Int,
    val trimArgb: Int
)

data class ChatRoomUiState(
    val roomCode: String = "",
    val roomTitle: String = "",
    val myPeerId: String = "",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionLabel: String = "Desconectado",
    val errorMessage: String? = null,
    val selectedCharacterId: String = "custom",
    val selectedRounds: Int = 3,
    val members: List<ChatRoomMember> = emptyList(),
    val messages: List<ChatRoomMessage> = emptyList(),
    val incomingChallenge: ChatChallenge? = null,
    val outgoingChallengeId: String? = null,
    val outgoingChallengeTargetName: String? = null,
    val activeBoutId: String? = null
)

object ChatCodeGenerator {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generateRoomCode(length: Int = 6): String =
        buildString(length) {
            repeat(length) {
                append(ALPHABET[Random.nextInt(ALPHABET.length)])
            }
        }

    fun normalizeCode(raw: String): String =
        raw.trim().uppercase().filter { it.isLetterOrDigit() }.take(8)

    fun buildLocalMember(
        peerId: String,
        playerName: String,
        gloveId: String,
        characterId: String,
        appearance: BoxerAppearance,
        nowMs: Long = System.currentTimeMillis()
    ): ChatRoomMember {
        val glove = GloveShopCatalog.byId(gloveId)
        if (characterId == "custom" || characterId.isBlank()) {
            return ChatRoomMember(
                peerId = peerId,
                name = playerName.trim().ifBlank { "Retador" },
                characterId = "custom",
                gloveId = gloveId,
                skinArgb = appearance.resolvedSkinColor().toArgb(),
                hairArgb = appearance.resolvedHairColor().toArgb(),
                trunksArgb = appearance.resolvedTrunksColor().toArgb(),
                gloveArgb = glove.primaryColor.toArgb(),
                trimArgb = glove.trimColor.toArgb(),
                lastSeenMs = nowMs
            )
        }
        val fighter = FighterRoster.byId(characterId)
        return ChatRoomMember(
            peerId = peerId,
            name = fighter.name,
            characterId = fighter.id,
            gloveId = gloveId,
            skinArgb = fighter.skinTone.toArgb(),
            hairArgb = fighter.hairColor.toArgb(),
            trunksArgb = fighter.trunksColor.toArgb(),
            gloveArgb = fighter.gloveColor.toArgb(),
            trimArgb = fighter.accentColor.toArgb(),
            lastSeenMs = nowMs
        )
    }
}
