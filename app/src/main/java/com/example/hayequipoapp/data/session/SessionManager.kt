package com.example.hayequipoapp.data.session

import com.example.hayequipoapp.data.model.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor() {

    private val _currentPlayer = MutableStateFlow<Player?>(null)
    val currentPlayer = _currentPlayer.asStateFlow()

    val isAdmin: Boolean get() = _currentPlayer.value?.role == "admin"
    val currentPlayerId: String? get() = _currentPlayer.value?.id

    fun setPlayer(player: Player) {
        _currentPlayer.value = player
    }

    fun clear() {
        _currentPlayer.value = null
    }
}