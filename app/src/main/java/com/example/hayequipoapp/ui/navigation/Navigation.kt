package com.example.hayequipoapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.hayequipoapp.ui.auth.LoginScreen
import com.example.hayequipoapp.ui.groups.FriendGroupDetailScreen
import com.example.hayequipoapp.ui.groups.FriendGroupListScreen
import com.example.hayequipoapp.ui.home.HomeScreen
import com.example.hayequipoapp.ui.matches.MatchDetailScreen
import com.example.hayequipoapp.ui.matches.MatchFormScreen
import com.example.hayequipoapp.ui.matches.MatchListScreen
import com.example.hayequipoapp.ui.players.PlayerListScreen
import com.example.hayequipoapp.ui.players.PlayerProfileScreen
import com.example.hayequipoapp.ui.sports.SportFormScreen
import com.example.hayequipoapp.ui.sports.SportListScreen
import com.example.hayequipoapp.ui.venues.VenueDetailScreen
import com.example.hayequipoapp.ui.venues.VenueListScreen

object Routes {
    const val LOGIN          = "login"
    const val HOME           = "home"
    const val SPORT_LIST     = "sports"
    const val SPORT_FORM     = "sports/form?sportId={sportId}"
    const val VENUE_LIST     = "venues"
    const val VENUE_DETAIL   = "venues/{venueId}"
    const val MATCH_LIST     = "matches"
    const val MATCH_DETAIL   = "matches/{matchId}"
    const val MATCH_FORM     = "matches/new"
    const val PLAYER_LIST    = "players"
    const val PLAYER_PROFILE = "players/{playerId}"
    const val GROUP_LIST     = "groups"
    const val GROUP_DETAIL   = "groups/{groupId}"

    fun venueDetail(venueId: String)   = "venues/$venueId"
    fun matchDetail(matchId: String)   = "matches/$matchId"
    fun playerProfile(playerId: String) = "players/$playerId"
    fun groupDetail(groupId: String)   = "groups/$groupId"
    fun sportForm(sportId: String = "") = if (sportId.isBlank()) "sports/form" else "sports/form?sportId=$sportId"
}

@Composable
fun HayEquipoNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN) {
            LoginScreen(onLoginSuccess = { navController.navigate(Routes.HOME) {
                popUpTo(Routes.LOGIN) { inclusive = true }
            }})
        }

        composable(Routes.HOME) {
            HomeScreen(navController = navController)
        }

        composable(Routes.SPORT_LIST) {
            SportListScreen(navController = navController)
        }

        composable(
            route = Routes.SPORT_FORM,
            arguments = listOf(navArgument("sportId") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { back ->
            val sportId = back.arguments?.getString("sportId") ?: ""
            SportFormScreen(sportId = sportId, onBack = { navController.popBackStack() })
        }

        composable(Routes.VENUE_LIST) {
            VenueListScreen(onVenueClick = { navController.navigate(Routes.venueDetail(it)) })
        }

        composable(
            route = Routes.VENUE_DETAIL,
            arguments = listOf(navArgument("venueId") { type = NavType.StringType })
        ) { back ->
            val venueId = back.arguments?.getString("venueId") ?: return@composable
            VenueDetailScreen(venueId = venueId, onBack = { navController.popBackStack() })
        }

        composable(Routes.MATCH_LIST) {
            MatchListScreen(
                onMatchClick = { navController.navigate(Routes.matchDetail(it)) },
                onNewMatch   = { navController.navigate(Routes.MATCH_FORM) },
                onSportsClick = { navController.navigate(Routes.SPORT_LIST) }
            )
        }

        composable(
            route = Routes.MATCH_DETAIL,
            arguments = listOf(navArgument("matchId") { type = NavType.StringType })
        ) { back ->
            val matchId = back.arguments?.getString("matchId") ?: return@composable
            MatchDetailScreen(matchId = matchId, onBack = { navController.popBackStack() })
        }

        composable(Routes.MATCH_FORM) {
            MatchFormScreen(
                onBack = { navController.popBackStack() },
                onManageSports = { navController.navigate(Routes.SPORT_LIST) }
            )
        }

        composable(Routes.PLAYER_LIST) {
            PlayerListScreen(onPlayerClick = { navController.navigate(Routes.playerProfile(it)) })
        }

        composable(
            route = Routes.PLAYER_PROFILE,
            arguments = listOf(navArgument("playerId") { type = NavType.StringType })
        ) { back ->
            val playerId = back.arguments?.getString("playerId") ?: return@composable
            PlayerProfileScreen(playerId = playerId, onBack = { navController.popBackStack() })
        }

        composable(Routes.GROUP_LIST) {
            FriendGroupListScreen(
                onGroupClick = { navController.navigate(Routes.groupDetail(it)) }
            )
        }

        composable(
            route = Routes.GROUP_DETAIL,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { back ->
            val groupId = back.arguments?.getString("groupId") ?: return@composable
            FriendGroupDetailScreen(groupId = groupId, onBack = { navController.popBackStack() })
        }
    }
}
