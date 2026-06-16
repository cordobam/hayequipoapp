package com.example.hayequipoapp.domain.repository

import com.example.hayequipoapp.data.model.FriendGroup
import com.example.hayequipoapp.data.model.Match
import com.example.hayequipoapp.data.model.MatchInvitation
import com.example.hayequipoapp.data.model.MatchStat
import com.example.hayequipoapp.data.model.Player
import com.example.hayequipoapp.data.model.PlayerReview
import com.example.hayequipoapp.data.model.PlayerStat
import com.example.hayequipoapp.data.model.Sport
import com.example.hayequipoapp.data.model.Venue
import kotlinx.coroutines.flow.Flow

interface SportRepository {
    fun getSports(): Flow<List<Sport>>
    suspend fun getSportById(sportId: String): Sport?
    suspend fun createSport(sport: Sport): Result<String>
    suspend fun updateSport(sport: Sport): Result<Unit>
    suspend fun deleteSport(sportId: String): Result<Unit>

}

interface VenueRepository {
    fun getVenues(): Flow<List<Venue>>
    fun getVenuesBySport(sportId: String): Flow<List<Venue>>
    suspend fun getVenueById(venueId: String): Venue?
    suspend fun createVenue(venue: Venue): Result<String>
    suspend fun updateVenue(venue: Venue): Result<Unit>
    suspend fun deleteVenue(venueId: String): Result<Unit>
}

interface PlayerRepository {
    fun getPlayers(): Flow<List<Player>>
    fun getAvailablePlayersBySport(sportId: String): Flow<List<Player>>
    suspend fun getPlayerById(playerId: String): Player?
    suspend fun getPlayerByUid(uid: String): Player?
    suspend fun createPlayer(player: Player): Result<String>
    suspend fun updatePlayer(player: Player): Result<Unit>
    suspend fun deletePlayer(playerId: String): Result<Unit>
}

interface FriendGroupRepository {
    fun getFriendGroupsByPlayer(playerId: String): Flow<List<FriendGroup>>
    suspend fun getFriendGroupById(groupId: String): FriendGroup?
    suspend fun createFriendGroup(group: FriendGroup): Result<String>
    suspend fun updateFriendGroup(group: FriendGroup): Result<Unit>
    suspend fun deleteFriendGroup(groupId: String): Result<Unit>
}

interface MatchRepository {
    fun getUpcomingMatches(): Flow<List<Match>>
    fun getMatchesBySport(sportId: String): Flow<List<Match>>
    fun getMatchesForPlayer(playerId: String): Flow<List<Match>>
    suspend fun getMatchById(matchId: String): Match?
    suspend fun createMatch(match: Match): Result<String>
    suspend fun updateMatch(match: Match): Result<Unit>
    suspend fun updateMatchStatus(matchId: String, status: String): Result<Unit>
}

interface MatchInvitationRepository {
    fun getPendingInvitationsForPlayer(playerId: String): Flow<List<MatchInvitation>>
    fun getInvitationsForMatch(matchId: String): Flow<List<MatchInvitation>>
    suspend fun createInvitation(invitation: MatchInvitation): Result<String>
    suspend fun updateInvitationStatus(invitationId: String, status: String): Result<Unit>
}

interface MatchStatRepository {
    fun getStatsForMatch(matchId: String): Flow<List<MatchStat>>
    fun getStatsForPlayer(playerId: String): Flow<List<MatchStat>>
    suspend fun createOrUpdateMatchStat(stat: MatchStat): Result<Unit>
}

interface PlayerStatRepository {
    suspend fun getPlayerStatBySport(playerId: String, sportId: String): PlayerStat?
    fun getAllStatsForPlayer(playerId: String): Flow<List<PlayerStat>>
}

interface PlayerReviewRepository {
    fun getReviewsForPlayer(reviewedId: String): Flow<List<PlayerReview>>
    fun getReviewsForMatch(matchId: String): Flow<List<PlayerReview>>
    suspend fun createReview(review: PlayerReview): Result<String>
    suspend fun deleteReview(reviewId: String): Result<Unit>
}
