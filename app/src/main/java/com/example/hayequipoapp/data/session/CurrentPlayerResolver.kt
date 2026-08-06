package com.example.hayequipoapp.data.session

import android.util.Log
import com.example.hayequipoapp.domain.repository.PlayerRepository
import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrentPlayerResolver @Inject constructor(
    private val auth: FirebaseAuth,
    private val playerRepository: PlayerRepository,
    private val sessionManager: SessionManager
) {
    suspend fun id(): String? {
        sessionManager.currentPlayerId?.let { return it }
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val player = playerRepository.getPlayerByUid(uid)
            if (player != null) {
                sessionManager.setPlayer(player)
                player.id
            } else {
                Log.e("CurrentPlayerResolver", "No existe jugador para uid $uid")
                null
            }
        } catch (e: Exception) {
            Log.e("CurrentPlayerResolver", "Error resolviendo jugador propio", e)
            null
        }
    }
}
