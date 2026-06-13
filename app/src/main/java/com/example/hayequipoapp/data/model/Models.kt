package com.example.hayequipoapp.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

// ─── Sport ────────────────────────────────────────────────
data class Sport(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val type: String = "team",           // "team" | "individual" | "pairs"
    val playersPerTeam: Int = 5,
    val minPlayersPerTeam: Int = 4,
    val maxPlayersPerTeam: Int = 7,
    val teamCount: Int = 2,
    val scoringUnit: String = "goles",   // "goles" | "puntos" | "sets"
    val durationMinutes: Int = 40,
    val iconUrl: String = "",
    val isActive: Boolean = true
)

// ─── Venue ────────────────────────────────────────────────
data class Venue(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val location: GeoPoint? = null,
    val phone: String = "",
    val photos: List<String> = emptyList(),
    val sportIds: List<String> = emptyList(),
    val pricePerHour: Double = 0.0,
    val amenities: List<String> = emptyList(),
    val rating: Double = 0.0,
    val schedules: Map<String, ScheduleDay> = emptyMap(),
    val createdBy: String = ""
)

data class ScheduleDay(
    val open: String = "08:00",
    val close: String = "23:00",
    val isOpen: Boolean = true
)

// ─── Player ───────────────────────────────────────────────
data class Player(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val preferredSports: List<String> = emptyList(),
    val preferredVenues: List<String> = emptyList(),
    val friendGroupIds: List<String> = emptyList(),
    val position: String = "",
    val skillLevel: Int = 3,             // 1–5
    val isAvailable: Boolean = true,
    val showReviews: Boolean = true,
    val fcmToken: String = "",
    val createdAt: Timestamp? = null
)

// ─── FriendGroup ──────────────────────────────────────────
data class FriendGroup(
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val memberIds: List<String> = emptyList(),
    val sportIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null
)

// ─── Match ────────────────────────────────────────────────
data class Match(
    val id: String = "",
    val sportId: String = "",
    val venueId: String = "",
    val organizerId: String = "",
    val groupId: String? = null,
    val title: String = "",
    val description: String = "",
    val date: Timestamp? = null,
    val durationMinutes: Int = 60,
    val status: String = "scheduled",
    // "scheduled" → "confirmed" → "in_progress" → "finished" | "cancelled"
    val pricePerPlayer: Double = 0.0,
    val teams: List<MatchTeam> = emptyList(),
    val playersNeeded: Int = 0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class MatchTeam(
    val name: String = "",
    val playerIds: List<String> = emptyList(),
    val score: Int? = null
)

// ─── MatchInvitation ──────────────────────────────────────
data class MatchInvitation(
    val id: String = "",
    val matchId: String = "",
    val groupId: String? = null,
    val playerId: String = "",
    val invitedBy: String = "",
    val status: String = "pending",
    // "pending" | "accepted" | "rejected" | "confirmed" | "attended" | "no_show"
    val isFromSearch: Boolean = false,
    val respondedAt: Timestamp? = null,
    val createdAt: Timestamp? = null
)

// ─── MatchStat ────────────────────────────────────────────
data class MatchStat(
    val id: String = "",                 // composite: matchId_playerId
    val matchId: String = "",
    val playerId: String = "",
    val teamIndex: Int = 0,
    val stats: Map<String, Int> = emptyMap(),
    val rating: Double = 0.0,
    val createdAt: Timestamp? = null
)

// ─── PlayerStat ───────────────────────────────────────────
data class PlayerStat(
    val id: String = "",                 // composite: playerId_sportId
    val playerId: String = "",
    val sportId: String = "",
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val matchesLost: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val extraStats: Map<String, Int> = emptyMap(),
    val averageReliability: Double = 0.0,
    val totalReviews: Int = 0,
    val wouldPlayAgainCount: Int = 0,
    val updatedAt: Timestamp? = null
)

// ─── PlayerReview ─────────────────────────────────────────
data class PlayerReview(
    val id: String = "",
    val matchId: String = "",
    val reviewerId: String = "",
    val reviewedId: String = "",
    val reliability: Int = 5,            // 1–5
    val skill: Int = 3,                  // 1–5
    val attitude: Int = 5,               // 1–5
    val comment: String = "",
    val wouldPlayAgain: Boolean = true,
    val createdAt: Timestamp? = null
)
