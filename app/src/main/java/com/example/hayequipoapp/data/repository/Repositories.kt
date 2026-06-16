package com.example.hayequipoapp.data.repository

import com.example.hayequipoapp.data.firebase.FirebaseSource
import com.example.hayequipoapp.data.model.FriendGroup
import com.example.hayequipoapp.data.model.Match
import com.example.hayequipoapp.data.model.MatchInvitation
import com.example.hayequipoapp.data.model.MatchStat
import com.example.hayequipoapp.data.model.Player
import com.example.hayequipoapp.data.model.PlayerReview
import com.example.hayequipoapp.data.model.Sport
import com.example.hayequipoapp.data.model.Venue
import com.example.hayequipoapp.domain.repository.*
import javax.inject.Inject

class SportRepositoryImpl @Inject constructor(private val source: FirebaseSource) : SportRepository {
    override fun getSports() = source.getSports()
    override suspend fun getSportById(sportId: String) = source.getSportById(sportId)
    override suspend fun createSport(sport: Sport) = runCatching { source.createSport(sport) }
    override suspend fun updateSport(sport: Sport) = runCatching { source.updateSport(sport) }
    override suspend fun deleteSport(sportId: String) = runCatching { source.deleteSport(sportId) }

}

class VenueRepositoryImpl @Inject constructor(private val source: FirebaseSource) : VenueRepository {
    override fun getVenues() = source.getVenues()
    override fun getVenuesBySport(sportId: String) = source.getVenuesBySport(sportId)
    override suspend fun getVenueById(venueId: String) = source.getVenueById(venueId)
    override suspend fun createVenue(venue: Venue) = runCatching { source.createVenue(venue) }
    override suspend fun updateVenue(venue: Venue) = runCatching { source.updateVenue(venue) }
    override suspend fun deleteVenue(venueId: String) = runCatching { source.deleteVenue(venueId) }
}

class PlayerRepositoryImpl @Inject constructor(private val source: FirebaseSource) : PlayerRepository {
    override fun getPlayers() = source.getPlayers()
    override fun getAvailablePlayersBySport(sportId: String) = source.getAvailablePlayersBySport(sportId)
    override suspend fun getPlayerById(playerId: String) = source.getPlayerById(playerId)
    override suspend fun getPlayerByUid(uid: String) = source.getPlayerByUid(uid)
    override suspend fun createPlayer(player: Player) = runCatching { source.createPlayer(player) }
    override suspend fun updatePlayer(player: Player) = runCatching { source.updatePlayer(player) }
    override suspend fun deletePlayer(playerId: String) = runCatching { source.deletePlayer(playerId) }
}

class FriendGroupRepositoryImpl @Inject constructor(private val source: FirebaseSource) : FriendGroupRepository {
    override fun getFriendGroupsByPlayer(playerId: String) = source.getFriendGroupsByPlayer(playerId)
    override suspend fun getFriendGroupById(groupId: String) = source.getFriendGroupById(groupId)
    override suspend fun createFriendGroup(group: FriendGroup) = runCatching { source.createFriendGroup(group) }
    override suspend fun updateFriendGroup(group: FriendGroup) = runCatching { source.updateFriendGroup(group) }
    override suspend fun deleteFriendGroup(groupId: String) = runCatching { source.deleteFriendGroup(groupId) }
}

class MatchRepositoryImpl @Inject constructor(private val source: FirebaseSource) : MatchRepository {
    override fun getUpcomingMatches() = source.getUpcomingMatches()
    override fun getMatchesBySport(sportId: String) = source.getMatchesBySport(sportId)
    override fun getMatchesForPlayer(playerId: String) = source.getMatchesForPlayer(playerId)
    override suspend fun getMatchById(matchId: String) = source.getMatchById(matchId)
    override suspend fun createMatch(match: Match) = runCatching { source.createMatch(match) }
    override suspend fun updateMatch(match: Match) = runCatching { source.updateMatch(match) }
    override suspend fun updateMatchStatus(matchId: String, status: String) =
        runCatching { source.updateMatchStatus(matchId, status) }
}

class MatchInvitationRepositoryImpl @Inject constructor(private val source: FirebaseSource) : MatchInvitationRepository {
    override fun getPendingInvitationsForPlayer(playerId: String) = source.getPendingInvitationsForPlayer(playerId)
    override fun getInvitationsForMatch(matchId: String) = source.getInvitationsForMatch(matchId)
    override suspend fun createInvitation(invitation: MatchInvitation) = runCatching { source.createInvitation(invitation) }
    override suspend fun updateInvitationStatus(invitationId: String, status: String) =
        runCatching { source.updateInvitationStatus(invitationId, status) }
}

class MatchStatRepositoryImpl @Inject constructor(private val source: FirebaseSource) : MatchStatRepository {
    override fun getStatsForMatch(matchId: String) = source.getStatsForMatch(matchId)
    override fun getStatsForPlayer(playerId: String) = source.getStatsForPlayer(playerId)
    override suspend fun createOrUpdateMatchStat(stat: MatchStat) = runCatching { source.createOrUpdateMatchStat(stat) }
}

class PlayerStatRepositoryImpl @Inject constructor(private val source: FirebaseSource) : PlayerStatRepository {
    override suspend fun getPlayerStatBySport(playerId: String, sportId: String) =
        source.getPlayerStatBySport(playerId, sportId)
    override fun getAllStatsForPlayer(playerId: String) = source.getAllStatsForPlayer(playerId)
}

class PlayerReviewRepositoryImpl @Inject constructor(private val source: FirebaseSource) : PlayerReviewRepository {
    override fun getReviewsForPlayer(reviewedId: String) = source.getReviewsForPlayer(reviewedId)
    override fun getReviewsForMatch(matchId: String) = source.getReviewsForMatch(matchId)
    override suspend fun createReview(review: PlayerReview) = runCatching { source.createReview(review) }
    override suspend fun deleteReview(reviewId: String) = runCatching { source.deleteReview(reviewId) }
}
