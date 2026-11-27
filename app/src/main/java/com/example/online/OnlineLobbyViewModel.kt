package com.example.online

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnlineLobbyViewModel(
    private val repository: OnlineRepository,
    private val currentUserId: String
) : ViewModel() {

    val profileState: StateFlow<OnlineUiState<BoxerProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<BoxerProfile?, OnlineUiState<BoxerProfile?>> { OnlineUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing profile", rootError)
                emit(OnlineUiState.Error(rootError.message ?: "Error al cargar el perfil online"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = OnlineUiState.Loading
            )

    val waitingRoomsState: StateFlow<OnlineUiState<List<OnlineMatch>>> =
        repository.observeWaitingMatches()
            .map<List<OnlineMatch>, OnlineUiState<List<OnlineMatch>>> { OnlineUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing waiting rooms", rootError)
                emit(OnlineUiState.Error(rootError.message ?: "Error al cargar salas online"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = OnlineUiState.Loading
            )

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun clearError() {
        _actionError.value = null
    }

    fun syncProfile(displayName: String, equippedGloveId: String) {
        viewModelScope.launch {
            repository.ensureUserProfile(displayName, equippedGloveId)
        }
    }

    fun createRoom(
        hostName: String,
        hostGloveId: String,
        maxRounds: Int,
        onRoomReady: (String) -> Unit
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _actionError.value = null
            repository.ensureUserProfile(hostName, hostGloveId)
            val result = repository.createMatch(hostName, hostGloveId, maxRounds)
            _busy.value = false
            result.onSuccess { code ->
                onRoomReady(code)
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "No se pudo crear la sala online."
            }
        }
    }

    fun joinRoom(
        roomCode: String,
        guestName: String,
        guestGloveId: String,
        onRoomReady: (String) -> Unit
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _actionError.value = null
            repository.ensureUserProfile(guestName, guestGloveId)
            val result = repository.joinMatch(roomCode, guestName, guestGloveId)
            _busy.value = false
            result.onSuccess { code ->
                onRoomReady(code)
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "No se pudo unir a la sala."
            }
        }
    }

    private companion object {
        const val TAG = "OnlineLobbyVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
