package com.example.online

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface OnlineUiState<out T> {
    data object Loading : OnlineUiState<Nothing>
    data class Success<T>(val data: T) : OnlineUiState<T>
    data class Error(val message: String) : OnlineUiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class BoxerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Retador",
    val equippedGloveId: String = "classic_red",
    val onlineWins: Long = 0L,
    val onlineLosses: Long = 0L,
    val onlineKos: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OnlineMatch(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val hostUid: String = "",
    val guestUid: String? = null,
    val participantUids: List<String> = emptyList(),
    val hostName: String = "Retador",
    val guestName: String? = null,
    val hostGloveId: String = "classic_red",
    val guestGloveId: String? = null,
    val status: String = "WAITING",
    val maxRounds: Long = 3L,
    val hostHp: Long = 150L,
    val guestHp: Long = 150L,
    val hostStamina: Long = 100L,
    val guestStamina: Long = 100L,
    val hostPosX: Double = -0.35,
    val guestPosX: Double = 0.35,
    val hostGuarding: Boolean = false,
    val guestGuarding: Boolean = false,
    val hostDodgeSeq: Long = 0L,
    val guestDodgeSeq: Long = 0L,
    val hostPunchType: String = "NONE",
    val guestPunchType: String = "NONE",
    val hostPunchSeq: Long = 0L,
    val guestPunchSeq: Long = 0L,
    val hostPunchDamage: Long = 0L,
    val guestPunchDamage: Long = 0L,
    val hostHitsLanded: Long = 0L,
    val guestHitsLanded: Long = 0L,
    val hostKnockdowns: Long = 0L,
    val guestKnockdowns: Long = 0L,
    val winnerUid: String? = null,
    val winMethod: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
