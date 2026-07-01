package com.example.hayequipoapp.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.hayequipoapp.domain.repository.MatchInvitationRepository
import com.example.hayequipoapp.domain.repository.MatchRepository
import com.google.firebase.auth.FirebaseAuth
import com.example.hayequipoapp.data.model.Match
import com.example.hayequipoapp.data.model.MatchInvitation
import com.example.hayequipoapp.ui.common.UiState
import com.example.hayequipoapp.ui.navigation.HayEquipoNavHost
import com.example.hayequipoapp.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.hayequipoapp.ui.groups.FriendGroupListScreen
import com.example.hayequipoapp.ui.matches.MatchListScreen
import com.example.hayequipoapp.ui.players.PlayerListScreen
import com.example.hayequipoapp.ui.venues.VenueListScreen
import com.example.hayequipoapp.data.session.SessionManager


// ─── ViewModel ────────────────────────────────────────────
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
    private val invitationRepository: MatchInvitationRepository,
    private val auth: FirebaseAuth,
    val sessionManager: SessionManager
) : ViewModel() {

    private val _upcomingMatches = MutableStateFlow<UiState<List<Match>>>(UiState.Loading)
    val upcomingMatches = _upcomingMatches.asStateFlow()

    private val _pendingInvitations = MutableStateFlow<UiState<List<MatchInvitation>>>(UiState.Loading)
    val pendingInvitations = _pendingInvitations.asStateFlow()

    val currentUid: String? get() = auth.currentUser?.uid

    init { load() }

    fun load() {
        viewModelScope.launch {
            matchRepository.getUpcomingMatches().collect { list ->
                _upcomingMatches.value = UiState.Success(list.take(5))
            }
        }
        viewModelScope.launch {
            val uid = currentUid ?: return@launch
            invitationRepository.getPendingInvitationsForPlayer(uid).collect { list ->
                _pendingInvitations.value = UiState.Success(list)
            }
        }
    }
}

// ─── Bottom Nav items ─────────────────────────────────────
private data class NavItem(val label: String, val icon: ImageVector, val route: String)
private val navItems = listOf(
    NavItem("Inicio",   Icons.Filled.Home,       Routes.HOME),
    NavItem("Partidos", Icons.Filled.SportsSoccer, Routes.MATCH_LIST),
    NavItem("Jugadores",Icons.Filled.People,     Routes.PLAYER_LIST),
    NavItem("Grupos",   Icons.Filled.Group,      Routes.GROUP_LIST),
    NavItem("Sedes",    Icons.Filled.Place,      Routes.VENUE_LIST)
)

// ─── HomeScreen (shell with bottom nav) ───────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val innerNav = rememberNavController()
    val backStack by innerNav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val viewModel: HomeViewModel = hiltViewModel()
    val currentPlayer by viewModel.sessionManager.currentPlayer.collectAsState()
    val isAdmin = currentPlayer?.role == "admin"

    val filteredNavItems = remember(navItems, isAdmin) {
        if (isAdmin) navItems
        else navItems.filter { it.route !in listOf(Routes.VENUE_LIST) }
    }

    Scaffold(
        bottomBar = {
            if (filteredNavItems.size > 1) {
                NavigationBar {
                    filteredNavItems.forEach { item ->
                        NavigationBarItem(
                            selected  = currentRoute == item.route,
                            onClick   = {
                                if (currentRoute != item.route) {
                                    innerNav.navigate(item.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState   = true
                                    }
                                }
                            },
                            icon  = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(navController = innerNav, startDestination = Routes.HOME) {
                composable(Routes.HOME) { HomeDashboard(navController) }
                composable(Routes.MATCH_LIST) { MatchListScreen(
                    onMatchClick   = { navController.navigate(Routes.matchDetail(it)) },
                    onNewMatch     = { navController.navigate(Routes.MATCH_FORM) },
                    onSportsClick  = if (isAdmin) { { navController.navigate(Routes.SPORT_LIST) } }
                    else { {} }
                ) }
                composable(Routes.PLAYER_LIST) { PlayerListScreen(
                    onPlayerClick = { navController.navigate(Routes.playerProfile(it)) },
                    onProfileClick = { currentPlayer?.id?.let { navController.navigate(Routes.playerProfile(it)) } }
                ) }
                composable(Routes.GROUP_LIST) { FriendGroupListScreen(
                    onGroupClick = { navController.navigate(Routes.groupDetail(it)) }
                ) }
                composable(Routes.VENUE_LIST) { VenueListScreen(
                    onVenueClick = { navController.navigate(Routes.venueDetail(it)) },
                    onNewVenue   = if (isAdmin) { { navController.navigate(Routes.venueForm()) } }
                    else { {} }
                ) }
            }
        }
    }
}

// ─── HomeDashboard (first tab) ────────────────────────────
@Composable
fun HomeDashboard(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val matches     by viewModel.upcomingMatches.collectAsState()
    val invitations by viewModel.pendingInvitations.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text("Próximos partidos", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        when (matches) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Error   -> Text((matches as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> {
                val list = (matches as UiState.Success).data
                if (list.isEmpty()) {
                    Text("Sin partidos próximos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    list.forEach { match ->
                        MatchSummaryCard(match = match, onClick = {
                            navController.navigate(Routes.matchDetail(match.id))
                        })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            else ->{}
        }

        Spacer(Modifier.height(24.dp))
        Text("Invitaciones pendientes", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        when (invitations) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Error   -> Text((invitations as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> {
                val list = (invitations as UiState.Success).data
                if (list.isEmpty()) {
                    Text("Sin invitaciones pendientes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    list.forEach { inv ->
                        InvitationCard(invitation = inv, onAccept = {}, onReject = {})
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun MatchSummaryCard(match: Match, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(match.title, style = MaterialTheme.typography.titleLarge)
            Text("Deporte: ${match.sportId}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InvitationCard(
    invitation: MatchInvitation,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Partido: ${invitation.matchId}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAccept) { Text("Voy") }
            TextButton(onClick = onReject) { Text("No puedo") }
        }
    }
}
