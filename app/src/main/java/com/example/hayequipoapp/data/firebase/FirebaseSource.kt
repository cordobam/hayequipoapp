package com.example.hayequipoapp.data.firebase

import com.example.hayequipoapp.data.model.FriendGroup
import com.example.hayequipoapp.data.model.Match
import com.example.hayequipoapp.data.model.MatchInvitation
import com.example.hayequipoapp.data.model.MatchStat
import com.example.hayequipoapp.data.model.Player
import com.example.hayequipoapp.data.model.PlayerReview
import com.example.hayequipoapp.data.model.PlayerStat
import com.example.hayequipoapp.data.model.Sport
import com.example.hayequipoapp.data.model.Venue
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSource @Inject constructor(
    private val db: FirebaseFirestore
) {

    // ─── Collections ──────────────────────────────────────
    private val sports         get() = db.collection("sports")
    private val venues         get() = db.collection("venues")
    private val players        get() = db.collection("players")
    private val friendGroups   get() = db.collection("friend_groups")
    private val matches        get() = db.collection("matches")
    private val invitations    get() = db.collection("match_invitations")
    private val matchStats     get() = db.collection("match_stats")
    private val playerStats    get() = db.collection("player_stats")
    private val playerReviews  get() = db.collection("player_reviews")

    // ─── Sports ───────────────────────────────────────────

    fun getSports(): Flow<List<Sport>> = sports
        .whereEqualTo("active", true)
        .asFlow()

    suspend fun getSportById(sportId: String): Sport? =
        sports.document(sportId).get().await().toObject(Sport::class.java)

    suspend fun createSport(sport: Sport): String {
        val ref = sports.document()
        ref.set(sport.copy(id = ref.id)).await()
        return ref.id
    }

    suspend fun updateSport(sport: Sport) {
        sports.document(sport.id).set(sport).await()
    }

    suspend fun deleteSport(sportId: String) {
        sports.document(sportId).delete().await()
    }

    // ─── Venues ───────────────────────────────────────────

    fun getVenues(): Flow<List<Venue>> = venues.asFlow()

    fun getVenuesBySport(sportId: String): Flow<List<Venue>> =
        venues.whereArrayContains("sportIds", sportId).asFlow()

    suspend fun getVenueById(venueId: String): Venue? =
        venues.document(venueId).get().await().toObject(Venue::class.java)

    suspend fun createVenue(venue: Venue): String {
        val ref = venues.document()
        ref.set(venue.copy(id = ref.id)).await()
        return ref.id
    }

    suspend fun updateVenue(venue: Venue) {
        venues.document(venue.id).set(venue).await()
    }

    suspend fun deleteVenue(venueId: String) {
        venues.document(venueId).delete().await()
    }

    // ─── Players ──────────────────────────────────────────

    fun getPlayers(): Flow<List<Player>> = players.asFlow()

    fun getAvailablePlayersBySport(sportId: String): Flow<List<Player>> =
        players
            .whereArrayContains("preferredSports", sportId)
            .whereEqualTo("isAvailable", true)
            .asFlow()

    suspend fun getPlayerById(playerId: String): Player? =
        players.document(playerId).get().await().toObject(Player::class.java)

    suspend fun getPlayerByUid(uid: String): Player? =
        players.whereEqualTo("uid", uid).limit(1)
            .get().await().documents.firstOrNull()
            ?.toObject(Player::class.java)

    suspend fun createPlayer(player: Player): String {
        val ref = players.document()
        ref.set(player.copy(id = ref.id)).await()
        return ref.id
    }

    suspend fun updatePlayer(player: Player) {
        players.document(player.id).set(player).await()
    }

    // ─── FriendGroups ─────────────────────────────────────

    fun getFriendGroupsByPlayer(playerId: String): Flow<List<FriendGroup>> =
        friendGroups.whereArrayContains("memberIds", playerId).asFlow()

    suspend fun getFriendGroupById(groupId: String): FriendGroup? =
        friendGroups.document(groupId).get().await().toObject(FriendGroup::class.java)

    suspend fun createFriendGroup(group: FriendGroup): String {
        val ref = friendGroups.document()
        ref.set(group.copy(id = ref.id)).await()
        return ref.id
    }

    suspend fun updateFriendGroup(group: FriendGroup) {
        friendGroups.document(group.id).set(group).await()
    }

    suspend fun deleteFriendGroup(groupId: String) {
        friendGroups.document(groupId).delete().await()
    }

    // ─── Matches ──────────────────────────────────────────

    fun getUpcomingMatches(): Flow<List<Match>> =
        matches
            .whereEqualTo("status", "scheduled")
            .whereGreaterThan("date", Timestamp.now())
            .orderBy("date")
            .asFlow()

    fun getMatchesBySport(sportId: String): Flow<List<Match>> =
        matches
            .whereEqualTo("sportId", sportId)
            .whereEqualTo("status", "scheduled")
            .whereGreaterThan("playersNeeded", 0)
            .asFlow()

    fun getMatchesForPlayer(playerId: String): Flow<List<Match>> =
        matches
            .whereGreaterThan("date", Timestamp.now())
            .orderBy("date")
            .asFlow() // filtrado de teams.playerIds se hace en el repositorio

    suspend fun getMatchById(matchId: String): Match? =
        matches.document(matchId).get().await().toObject(Match::class.java)

    suspend fun createMatch(match: Match): String {
        val ref = matches.document()
        ref.set(match.copy(id = ref.id, createdAt = Timestamp.now())).await()
        return ref.id
    }

    suspend fun updateMatch(match: Match) {
        matches.document(match.id).set(match.copy(updatedAt = Timestamp.now())).await()
    }

    suspend fun updateMatchStatus(matchId: String, status: String) {
        matches.document(matchId).update(
            "status", status,
            "updatedAt", Timestamp.now()
        ).await()
    }

    // ─── MatchInvitations ─────────────────────────────────

    fun getPendingInvitationsForPlayer(playerId: String): Flow<List<MatchInvitation>> =
        invitations
            .whereEqualTo("playerId", playerId)
            .whereEqualTo("status", "pending")
            .asFlow()

    fun getInvitationsForMatch(matchId: String): Flow<List<MatchInvitation>> =
        invitations.whereEqualTo("matchId", matchId).asFlow()

    suspend fun createInvitation(invitation: MatchInvitation): String {
        val ref = invitations.document()
        ref.set(invitation.copy(id = ref.id, createdAt = Timestamp.now())).await()
        return ref.id
    }

    suspend fun updateInvitationStatus(invitationId: String, status: String) {
        invitations.document(invitationId).update(
            "status", status,
            "respondedAt", Timestamp.now()
        ).await()
    }

    // ─── MatchStats ───────────────────────────────────────

    fun getStatsForMatch(matchId: String): Flow<List<MatchStat>> =
        matchStats.whereEqualTo("matchId", matchId).asFlow()

    fun getStatsForPlayer(playerId: String): Flow<List<MatchStat>> =
        matchStats
            .whereEqualTo("playerId", playerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .asFlow()

    suspend fun createOrUpdateMatchStat(stat: MatchStat) {
        matchStats.document(stat.id).set(stat).await()
    }

    // ─── PlayerStats ──────────────────────────────────────

    suspend fun getPlayerStatBySport(playerId: String, sportId: String): PlayerStat? {
        val id = "${playerId}_${sportId}"
        return playerStats.document(id).get().await().toObject(PlayerStat::class.java)
    }

    fun getAllStatsForPlayer(playerId: String): Flow<List<PlayerStat>> =
        playerStats.whereEqualTo("playerId", playerId).asFlow()

    // ─── PlayerReviews ────────────────────────────────────

    fun getReviewsForPlayer(reviewedId: String): Flow<List<PlayerReview>> =
        playerReviews
            .whereEqualTo("reviewedId", reviewedId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .asFlow()

    fun getReviewsForMatch(matchId: String): Flow<List<PlayerReview>> =
        playerReviews.whereEqualTo("matchId", matchId).asFlow()

    suspend fun createReview(review: PlayerReview): String {
        val ref = playerReviews.document()
        ref.set(review.copy(id = ref.id, createdAt = Timestamp.now())).await()
        return ref.id
    }

    suspend fun deleteReview(reviewId: String) {
        playerReviews.document(reviewId).delete().await()
    }
}

// ─── Extension: Query → Flow ──────────────────────────────
private inline fun <reified T> Query.asFlow(): Flow<List<T>> = callbackFlow {
    val listener = addSnapshotListener { snap, err ->
        if (err != null) { close(err); return@addSnapshotListener }
        val list = snap?.documents?.mapNotNull { it.toObject(T::class.java) } ?: emptyList()
        trySend(list)
    }
    awaitClose { listener.remove() }
}

private inline fun <reified T> CollectionReference.asFlow(): Flow<List<T>> =
    (this as Query).asFlow()
