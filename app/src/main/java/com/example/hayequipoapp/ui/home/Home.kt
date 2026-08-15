package com.example.hayequipoapp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.hayequipoapp.domain.repository.SportRepository
import com.example.hayequipoapp.domain.repository.VenueRepository
import com.example.hayequipoapp.data.model.Match
import com.example.hayequipoapp.data.model.MatchInvitation
import com.example.hayequipoapp.data.model.Sport
import com.example.hayequipoapp.data.model.Venue
import com.example.hayequipoapp.data.session.CurrentPlayerResolver
import com.example.hayequipoapp.data.session.HomeSeenStore
import com.example.hayequipoapp.ui.common.UiState
import com.example.hayequipoapp.ui.matches.MatchCard
import com.example.hayequipoapp.ui.navigation.HayEquipoNavHost
import com.example.hayequipoapp.ui.navigation.Routes
import com.google.firebase.Timestamp
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
    private val sportRepository: SportRepository,
    private val venueRepository: VenueRepository,
    private val resolver: CurrentPlayerResolver,
    private val seenStore: HomeSeenStore,
    val sessionManager: SessionManager
) : ViewModel() {

    private val _upcomingMatches = MutableStateFlow<UiState<List<Match>>>(UiState.Loading)
    val upcomingMatches = _upcomingMatches.asStateFlow()

    private val _myMatches = MutableStateFlow<UiState<List<Match>>>(UiState.Loading)
    val myMatches = _myMatches.asStateFlow()

    private val _pendingInvitations = MutableStateFlow<UiState<List<MatchInvitation>>>(UiState.Loading)
    val pendingInvitations = _pendingInvitations.asStateFlow()

    private val _upcomingNew = MutableStateFlow(0)
    val upcomingNew = _upcomingNew.asStateFlow()

    private val _playedNew = MutableStateFlow(0)
    val playedNew = _playedNew.asStateFlow()

    private val _invitationsNew = MutableStateFlow(0)
    val invitationsNew = _invitationsNew.asStateFlow()

    private val _matchesById = MutableStateFlow<Map<String, Match>>(emptyMap())
    val matchesById = _matchesById.asStateFlow()

    private val _sportsById = MutableStateFlow<Map<String, Sport>>(emptyMap())
    val sportsById = _sportsById.asStateFlow()

    private val _venuesById = MutableStateFlow<Map<String, Venue>>(emptyMap())
    val venuesById = _venuesById.asStateFlow()

    private val _myId = MutableStateFlow<String?>(null)
    val myId = _myId.asStateFlow()

    init {
        load()
        loadSportVenueMaps()
        warmUpSession()
    }

    fun load() {
        val lastSeen = seenStore.lastSeen()
        viewModelScope.launch {
            try {
                matchRepository.getUpcomingMatches().collect { list ->
                    val myId = resolver.id()
                    _myId.value = myId
                    val mine = list.filter { m ->
                        myId != null && (m.organizerId == myId || myId in m.participantIds)
                    }
                    _upcomingMatches.value = UiState.Success(mine.take(5))
                    _matchesById.value = list.associateBy { it.id }
                    _upcomingNew.value = mine.count { isNew(it.createdAt, lastSeen) }
                }
            } catch (e: Exception) {
                _upcomingMatches.value = UiState.Error(e.message ?: "Error cargando partidos")
            }
        }
        viewModelScope.launch {
            try {
                matchRepository.getPlayedMatches().collect { list ->
                    val myId = resolver.id() ?: _myId.value
                    val mine = list.filter { m ->
                        myId != null && (m.organizerId == myId || myId in m.participantIds)
                    }
                    _myMatches.value = UiState.Success(mine)
                    _matchesById.value = _matchesById.value + list.associateBy { it.id }
                    _playedNew.value = mine.count { isNew(it.updatedAt, lastSeen) }
                }
            } catch (e: Exception) {
                _myMatches.value = UiState.Error(e.message ?: "Error cargando partidos")
            }
        }
        viewModelScope.launch {
            val myId = resolver.id() ?: return@launch
            try {
                invitationRepository.getPendingInvitationsForPlayer(myId).collect { list ->
                    val valid = filterExpiredInvitations(list)
                    _pendingInvitations.value = UiState.Success(valid)
                    _invitationsNew.value = valid.count { isNew(it.createdAt, lastSeen) }
                }
            } catch (e: Exception) {
                _pendingInvitations.value = UiState.Error(e.message ?: "Error cargando invitaciones")
            }
        }
        seenStore.markSeen()
    }

    private fun isNew(timestamp: Timestamp?, lastSeen: Long): Boolean =
        timestamp != null && timestamp.seconds * 1000 > lastSeen

    fun joinMatch(matchId: String) {
        val me = _myId.value ?: return
        viewModelScope.launch {
            matchRepository.addMatchParticipant(matchId, me)
            load()
        }
    }

    private suspend fun filterExpiredInvitations(invitations: List<MatchInvitation>): List<MatchInvitation> {
        val result = mutableListOf<MatchInvitation>()
        for (inv in invitations) {
            val match = _matchesById.value[inv.matchId]
                ?: matchRepository.getMatchById(inv.matchId)?.also { m ->
                    _matchesById.value = _matchesById.value + (m.id to m)
                }
            val expired = match?.date?.let { it.seconds * 1000 < System.currentTimeMillis() } == true
            if (expired) {
                invitationRepository.updateInvitationStatus(inv.id, "rejected")
            } else {
                result.add(inv)
            }
        }
        return result
    }

    fun respondInvitation(inv: MatchInvitation, accepted: Boolean) {
        viewModelScope.launch {
            val status = if (accepted) "accepted" else "rejected"
            invitationRepository.updateInvitationStatus(inv.id, status)
            if (accepted) {
                val myId = resolver.id()
                if (myId != null && inv.matchId.isNotBlank()) {
                    matchRepository.addMatchParticipant(inv.matchId, myId)
                }
            }
            load()
        }
    }

    private fun warmUpSession() {
        viewModelScope.launch { resolver.id() }
    }

    private fun loadSportVenueMaps() {
        viewModelScope.launch {
            sportRepository.getSports().collect { list ->
                _sportsById.value = list.associateBy { it.id }
            }
        }
        viewModelScope.launch {
            venueRepository.getVenues().collect { list ->
                _venuesById.value = list.associateBy { it.id }
            }
        }
    }

    fun resolveMyProfileId(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val id = resolver.id()
            if (id != null) onResult(id)
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

    Scaffold(
        bottomBar = {
            if (navItems.size > 1) {
                NavigationBar {
                    navItems.forEach { item ->
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
                    onNewMatch     = { navController.navigate(Routes.matchForm()) },
                    onSportsClick  = if (isAdmin) { { navController.navigate(Routes.SPORT_LIST) } }
                    else { {} }
                ) }
                composable(Routes.PLAYER_LIST) { PlayerListScreen(
                    onPlayerClick = { navController.navigate(Routes.playerProfile(it)) },
                    onProfileClick = { viewModel.resolveMyProfileId { id ->
                        navController.navigate(Routes.playerProfile(id))
                    } }
                ) }
                composable(Routes.GROUP_LIST) { FriendGroupListScreen(
                    onGroupClick = { navController.navigate(Routes.groupDetail(it)) }
                ) }
                composable(Routes.VENUE_LIST) { VenueListScreen(
                    onVenueClick = { navController.navigate(Routes.venueDetail(it)) },
                    onNewVenue   = { navController.navigate(Routes.venueForm()) }
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
    val myMatches   by viewModel.myMatches.collectAsState()
    val invitations by viewModel.pendingInvitations.collectAsState()
    val matchesById by viewModel.matchesById.collectAsState()
    val sportsById  by viewModel.sportsById.collectAsState()
    val venuesById  by viewModel.venuesById.collectAsState()
    val myId        by viewModel.myId.collectAsState()
    val upcomingNew by viewModel.upcomingNew.collectAsState()
    val playedNew   by viewModel.playedNew.collectAsState()
    val invitationsNew by viewModel.invitationsNew.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        ExpandableSection(title = "Invitaciones pendientes", badgeCount = invitationsNew) {
            when (invitations) {
                is UiState.Loading -> CircularProgressIndicator()
                is UiState.Error   -> Text((invitations as UiState.Error).message, color = MaterialTheme.colorScheme.error)
                is UiState.Success -> {
                    val list = (invitations as UiState.Success).data
                    if (list.isEmpty()) {
                        Text("Sin invitaciones pendientes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        list.forEach { inv ->
                            InvitationCard(
                                invitation = inv,
                                matchTitle = matchesById[inv.matchId]?.title,
                                onAccept = { viewModel.respondInvitation(inv, true) },
                                onReject = { viewModel.respondInvitation(inv, false) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                else -> {}
            }
        }

        Spacer(Modifier.height(16.dp))
        ExpandableSection(title = "Mis partidos", badgeCount = playedNew) {
            when (myMatches) {
                is UiState.Loading -> CircularProgressIndicator()
                is UiState.Error   -> Text((myMatches as UiState.Error).message, color = MaterialTheme.colorScheme.error)
                is UiState.Success -> {
                    val list = (myMatches as UiState.Success).data
                    if (list.isEmpty()) {
                        Text("No jugaste partidos este mes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        list.forEach { match ->
                            MatchCard(
                                match = match,
                                myId = myId,
                                sportName = sportsById[match.sportId]?.name,
                                venueName = venuesById[match.venueId]?.name,
                                onClick = { navController.navigate(Routes.matchDetail(match.id)) },
                                onJoin = { viewModel.joinMatch(match.id) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                else -> {}
            }
        }

        Spacer(Modifier.height(16.dp))
        ExpandableSection(title = "Próximos partidos", badgeCount = upcomingNew) {
            when (matches) {
                is UiState.Loading -> CircularProgressIndicator()
                is UiState.Error   -> Text((matches as UiState.Error).message, color = MaterialTheme.colorScheme.error)
                is UiState.Success -> {
                    val list = (matches as UiState.Success).data
                    if (list.isEmpty()) {
                        Text("Sin partidos próximos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        list.forEach { match ->
                            MatchCard(
                                match = match,
                                myId = myId,
                                sportName = sportsById[match.sportId]?.name,
                                venueName = venuesById[match.venueId]?.name,
                                onClick = { navController.navigate(Routes.matchDetail(match.id)) },
                                onJoin = { viewModel.joinMatch(match.id) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    badgeCount: Int,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (badgeCount > 0) {
                Badge { Text(badgeCount.toString()) }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Colapsar $title" else "Expandir $title"
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun InvitationCard(
    invitation: MatchInvitation,
    matchTitle: String?,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(matchTitle ?: "Partido: ${invitation.matchId}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAccept) { Text("Voy") }
            TextButton(onClick = onReject) { Text("No puedo") }
        }
    }
}
