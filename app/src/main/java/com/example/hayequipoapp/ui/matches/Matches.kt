package com.example.hayequipoapp.ui.matches

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.hayequipoapp.domain.repository.MatchRepository
import com.example.hayequipoapp.ui.common.EmptyScreen
import com.example.hayequipoapp.ui.common.ErrorScreen
import com.example.hayequipoapp.ui.common.LoadingScreen
import com.example.hayequipoapp.ui.common.StatusChip
import com.example.hayequipoapp.ui.common.UiState
import com.google.firebase.Timestamp
import com.example.hayequipoapp.data.model.Match
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import com.example.hayequipoapp.data.session.CurrentPlayerResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.example.hayequipoapp.domain.repository.SportRepository
import com.example.hayequipoapp.domain.repository.VenueRepository
import com.example.hayequipoapp.data.model.Sport
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.util.Log
import com.example.hayequipoapp.data.model.MatchInvitation
import com.example.hayequipoapp.data.model.MatchTeam
import com.example.hayequipoapp.data.model.Player
import com.example.hayequipoapp.data.model.Venue
import com.example.hayequipoapp.data.session.SessionManager
import com.example.hayequipoapp.domain.repository.MatchInvitationRepository
import com.example.hayequipoapp.domain.repository.MatchStatRepository
import com.example.hayequipoapp.domain.repository.PlayerRepository
import com.example.hayequipoapp.domain.repository.PlayerStatRepository
import com.example.hayequipoapp.data.model.MatchStat
import com.example.hayequipoapp.data.model.PlayerStat
import com.example.hayequipoapp.ui.common.DropdownField
import com.example.hayequipoapp.ui.common.StarRating


// ─── Datos de reporte del partido ─────────────────────────
private const val TAG = "MatchDetail"

data class PlayerReport(
    val playerId: String,
    val goals: Int = 0,
    val assists: Int = 0,
    val rating: Double = 0.0
)


// ─── List ViewModel ───────────────────────────────────────
@HiltViewModel
class MatchListViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
    private val invitationRepository: MatchInvitationRepository,
    private val resolver: CurrentPlayerResolver,
    val sessionManager: SessionManager
) : ViewModel() {

    private val _matches = MutableStateFlow<UiState<List<Match>>>(UiState.Loading)
    val matches = _matches.asStateFlow()

    private val _myId = MutableStateFlow<String?>(null)
    val myId = _myId.asStateFlow()

    init {
        resolveMyId()
    }

    private fun resolveMyId() {
        viewModelScope.launch {
            _myId.value = resolver.id()
            loadMatches()
        }
    }

    fun loadMatches() {
        val myId = _myId.value
        viewModelScope.launch {
            val flow = if (myId != null) {
                combine(
                    matchRepository.getUpcomingMatches(),
                    invitationRepository.getPendingInvitationsForPlayer(myId)
                ) { matches, invitations ->
                    val invitedIds = invitations.mapNotNull { it.matchId }.toSet()
                    matches.filter { m ->
                        m.organizerId != myId &&
                            myId !in m.participantIds &&
                            m.id !in invitedIds
                    }
                }
            } else {
                matchRepository.getUpcomingMatches()
            }
            flow.collect { list ->
                _matches.value = UiState.Success(
                    list.sortedByDescending { m -> m.date }.take(5)
                )
            }
        }
    }

    fun joinMatch(matchId: String) {
        val me = _myId.value ?: return
        viewModelScope.launch {
            matchRepository.addMatchParticipant(matchId, me)
        }
    }
}

// ─── Detail ViewModel ─────────────────────────────────────
@HiltViewModel
class MatchDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val matchRepository: MatchRepository,
    private val invitationRepository: MatchInvitationRepository,
    private val playerRepository: PlayerRepository,
    private val sportRepository: SportRepository,
    private val matchStatRepository: MatchStatRepository,
    private val playerStatRepository: PlayerStatRepository,
    private val resolver: CurrentPlayerResolver
) : ViewModel() {

    private val matchId: String = checkNotNull(savedStateHandle["matchId"])

    private val _match = MutableStateFlow<UiState<Match>>(UiState.Loading)
    val match = _match.asStateFlow()

    private val _canInvite = MutableStateFlow(false)
    val canInvite = _canInvite.asStateFlow()

    private val _invitations = MutableStateFlow<UiState<List<MatchInvitation>>>(UiState.Loading)
    val invitations = _invitations.asStateFlow()

    private val _allPlayers = MutableStateFlow<List<Player>>(emptyList())
    val allPlayers = _allPlayers.asStateFlow()

    private val _myId = MutableStateFlow<String?>(null)
    val myId = _myId.asStateFlow()
    private val _invitedIds = MutableStateFlow<Set<String>>(emptySet())

    private val _availablePlayers = MutableStateFlow<UiState<List<Player>>>(UiState.Loading)
    val availablePlayers = _availablePlayers.asStateFlow()

    private val _inviteState = MutableStateFlow<UiState<String>?>(null)
    val inviteState = _inviteState.asStateFlow()

    private val _sport = MutableStateFlow<Sport?>(null)
    val sport = _sport.asStateFlow()

    private val _teamCount = MutableStateFlow(2)
    val teamCount = _teamCount.asStateFlow()

    private val _candidates = MutableStateFlow<List<String>>(emptyList())
    val candidates = _candidates.asStateFlow()

    private val _saveTeamsState = MutableStateFlow<UiState<String>?>(null)
    val saveTeamsState = _saveTeamsState.asStateFlow()

    private val _matchStats = MutableStateFlow<Map<String, MatchStat>>(emptyMap())
    val matchStats = _matchStats.asStateFlow()

    private val _reportState = MutableStateFlow<UiState<String>?>(null)
    val reportState = _reportState.asStateFlow()

    init {
        loadMatch()
        loadInvitations()
        loadAvailablePlayers()
    }

    fun loadMatch() {
        viewModelScope.launch {
            try {
                val m = matchRepository.getMatchById(matchId)
                _match.value = if (m != null) UiState.Success(m) else UiState.Error("Partido no encontrado")
                _myId.value = resolver.id()
                _canInvite.value = m?.organizerId != null && m.organizerId == _myId.value
                recomputeAvailablePlayers()
                recomputeCandidates()
                m?.sportId?.takeIf { it.isNotBlank() }?.let { loadSport(it) }
            } catch (e: Exception) {
                _match.value = UiState.Error(e.message ?: "Error al cargar partido")
            }
        }
    }

    private fun loadSport(sportId: String) {
        viewModelScope.launch {
            _sport.value = sportRepository.getSportById(sportId)
            _teamCount.value = _sport.value?.teamCount ?: 2
        }
    }

    fun loadInvitations() {
        viewModelScope.launch {
            try {
                invitationRepository.getInvitationsForMatch(matchId).collect { list ->
                    _invitations.value = UiState.Success(list)
                    _invitedIds.value = list.map { it.playerId }.toSet()
                    recomputeAvailablePlayers()
                    recomputeCandidates()
                }
            } catch (e: Exception) {
                _invitations.value = UiState.Error(e.message ?: "Error cargando invitaciones")
            }
        }
    }

    fun loadAvailablePlayers() {
        viewModelScope.launch {
            try {
                playerRepository.getPlayers().collect { list ->
                    _allPlayers.value = list
                    recomputeAvailablePlayers()
                }
            } catch (e: Exception) {
                _availablePlayers.value = UiState.Error(e.message ?: "Error cargando jugadores")
            }
        }
    }

    private fun recomputeAvailablePlayers() {
        val me = _myId.value
        _availablePlayers.value = UiState.Success(
            _allPlayers.value.filter { it.id != me && it.id !in _invitedIds.value }
        )
    }

    private fun recomputeCandidates() {
        val m = (_match.value as? UiState.Success)?.data ?: return
        val acceptedIds = (_invitations.value as? UiState.Success)?.data
            ?.filter { it.status == "accepted" }
            ?.map { it.playerId }
            ?: emptyList()
        _candidates.value = (m.participantIds + acceptedIds).distinct()
    }

    fun loadMatchStats() {
        viewModelScope.launch {
            runCatching {
                matchStatRepository.getStatsForMatch(matchId).collect { list ->
                    _matchStats.value = list.associateBy { it.playerId }
                }
            }
        }
    }

    private fun teamIndexFor(match: Match, playerId: String): Int =
        match.teams.indexOfFirst { playerId in it.playerIds }

    fun saveReport(
        data: List<PlayerReport>,
        teamScores: Map<Int, Int?>
    ) {
        val m = (_match.value as? UiState.Success)?.data ?: return
        val sportId = m.sportId
        if (sportId.isBlank()) {
            _reportState.value = UiState.Error("El partido no tiene deporte asociado")
            return
        }
        viewModelScope.launch {
            _reportState.value = UiState.Loading
            val winningIndex = teamScores
                .filterValues { it != null }
                .maxByOrNull { it.value ?: 0 }
                ?.key

            var statsFailed = false
            var scoresFailed = false

            // 1) Guardar MatchStat + acumular PlayerStat por jugador
            data.forEach { d ->
                val teamIndex = teamIndexFor(m, d.playerId)
                val stat = MatchStat(
                    id = "${matchId}_${d.playerId}",
                    matchId = matchId,
                    playerId = d.playerId,
                    teamIndex = teamIndex,
                    stats = mapOf("goles" to d.goals, "asistencias" to d.assists),
                    rating = d.rating,
                    createdAt = null
                )
                matchStatRepository.createOrUpdateMatchStat(stat)
                    .onFailure { e -> statsFailed = true; Log.e(TAG, "Error guardando MatchStat de ${d.playerId}", e) }

                val existing = _matchStats.value[d.playerId]
                _matchStats.value = _matchStats.value + (d.playerId to stat)

                val prev = playerStatRepository.getPlayerStatBySport(d.playerId, sportId)
                val alreadyCounted = existing != null

                val oldGoals = existing?.stats?.get("goles") ?: 0
                val oldAssists = existing?.stats?.get("asistencias") ?: 0
                val oldRating = existing?.rating ?: 0.0

                val oldReviews = if (alreadyCounted && oldRating > 0) 1 else 0
                val newReviews = if (d.rating > 0) 1 else 0
                val totalReviews = (prev?.totalReviews ?: 0) - oldReviews + newReviews
                val prevAvg = prev?.averageReliability ?: 0.0
                val prevCount = prev?.totalReviews ?: 0
                val avg = if (totalReviews > 0) {
                    ((prevAvg * prevCount) - oldRating + d.rating) / totalReviews
                } else 0.0

                val won = teamIndex >= 0 && winningIndex == teamIndex
                val lost = teamIndex >= 0 && winningIndex != null && teamIndex != winningIndex
                val newStat = PlayerStat(
                    id = "${d.playerId}_$sportId",
                    playerId = d.playerId,
                    sportId = sportId,
                    matchesPlayed = (prev?.matchesPlayed ?: 0) + if (alreadyCounted) 0 else 1,
                    matchesWon = (prev?.matchesWon ?: 0) + if (alreadyCounted) 0 else (if (won) 1 else 0),
                    matchesLost = (prev?.matchesLost ?: 0) + if (alreadyCounted) 0 else (if (lost) 1 else 0),
                    goals = (prev?.goals ?: 0) - oldGoals + d.goals,
                    assists = (prev?.assists ?: 0) - oldAssists + d.assists,
                    averageReliability = avg,
                    totalReviews = totalReviews,
                    updatedAt = null
                )
                playerStatRepository.upsertPlayerStat(newStat)
                    .onFailure { e -> statsFailed = true; Log.e(TAG, "Error al guardar PlayerStat de ${d.playerId}", e) }
            }

            // 2) Guardar scores de equipos
            val updatedTeams = m.teams.mapIndexed { idx, team ->
                team.copy(score = teamScores[idx] ?: team.score)
            }
            if (updatedTeams.isNotEmpty()) {
                matchRepository.updateMatch(m.copy(teams = updatedTeams))
                    .onFailure { e -> scoresFailed = true; Log.e(TAG, "Error al guardar scores de equipos", e) }
            }

            _reportState.value = when {
                statsFailed && scoresFailed ->
                    UiState.Error("No se pudieron guardar las estadísticas y los scores de los equipos.\nRevisá la conexión e intentá de nuevo.")
                statsFailed ->
                    UiState.Error("No se pudieron guardar las estadísticas de los jugadores.\nRevisá la conexión e intentá de nuevo.")
                scoresFailed ->
                    UiState.Error("No se pudieron guardar los scores de los equipos.\nRevisá la conexión e intentá de nuevo.")
                else -> UiState.Success("Reporte guardado")
            }
            loadMatch()
            loadMatchStats()
        }
    }

    fun saveTeams(teams: List<MatchTeam>) {
        val m = (_match.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _saveTeamsState.value = UiState.Loading
            val result = matchRepository.updateMatch(m.copy(teams = teams))
            _saveTeamsState.value = result.fold(
                onSuccess = { UiState.Success("Equipos guardados") },
                onFailure = { UiState.Error(it.message ?: "Error al guardar equipos") }
            )
            loadMatch()
        }
    }

    fun invitePlayers(invitedIds: List<String>) {
        if (invitedIds.isEmpty()) return
        viewModelScope.launch {
            val myId = resolver.id()
                ?: run { _inviteState.value = UiState.Error("No hay sesión activa"); return@launch }
            _inviteState.value = UiState.Loading
            var ok = true
            invitedIds.forEach { pid ->
                val invitation = MatchInvitation(matchId = matchId, playerId = pid, invitedBy = myId)
                if (invitationRepository.createInvitation(invitation).isFailure) ok = false
            }
            _inviteState.value = if (ok) UiState.Success("Invitaciones enviadas")
                else UiState.Error("Algunas invitaciones no se pudieron enviar")
        }
    }

    fun updateStatus(status: String) {
        viewModelScope.launch {
            if (matchRepository.updateMatchStatus(matchId, status).isSuccess) {
                loadMatch()
            }
        }
    }
}

// ─── Form ViewModel ───────────────────────────────────────

@HiltViewModel
class MatchFormViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
    private val resolver: CurrentPlayerResolver,
    private val sportRepository: SportRepository,
    private val venueRepository: VenueRepository
) : ViewModel() {

    private val _saved = MutableStateFlow<UiState<String>?>(null)
    val saved = _saved.asStateFlow()

    private val _sports = MutableStateFlow<UiState<List<Sport>>>(UiState.Loading)
    val sports = _sports.asStateFlow()

    private val _venues = MutableStateFlow<UiState<List<Venue>>>(UiState.Loading)
    val venues = _venues.asStateFlow()

    private val _preselectedVenue = MutableStateFlow<Venue?>(null)
    val preselectedVenue = _preselectedVenue.asStateFlow()

    fun loadVenueForMatchForm(venueId: String) {
        viewModelScope.launch {
            _preselectedVenue.value =
                if (venueId.isBlank()) null else venueRepository.getVenueById(venueId)
        }
    }

    fun loadSports() {                                                                 // ← NUEVO
        viewModelScope.launch {
            sportRepository.getSports().collect {
                _sports.value = UiState.Success(it)
            }
        }
    }

    fun loadVenues(sportId: String) {
        viewModelScope.launch {
            _venues.value = UiState.Loading
            venueRepository.getVenuesBySport(sportId).collect {
                _venues.value = UiState.Success(it)
            }
        }
    }

    fun createMatch(
        title: String,
        sportId: String,
        venueId: String,
        durationMinutes: Int,
        playersNeeded: Int,
        pricePerPlayer: Double,
        dateMillis: Long
    ) {
        viewModelScope.launch {
            val organizerId = resolver.id()
                ?: run { _saved.value = UiState.Error("No hay sesión activa"); return@launch }

            if (dateMillis <= System.currentTimeMillis()) {
                _saved.value = UiState.Error("Elegí una fecha futura")
                return@launch
            }

            _saved.value = UiState.Loading
            val match = Match(
                title           = title,
                sportId         = sportId,
                venueId         = venueId,
                organizerId     = organizerId,
                durationMinutes = durationMinutes,
                playersNeeded   = playersNeeded,
                pricePerPlayer  = pricePerPlayer,
                date            = Timestamp(Date(dateMillis))
            )
            val result = matchRepository.createMatch(match)
            _saved.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Error al crear partido") }
            )
        }
    }
}

// ─── MatchListScreen ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchListScreen(
    onMatchClick: (String) -> Unit,
    onNewMatch:   () -> Unit,
    onSportsClick: () -> Unit,
    viewModel: MatchListViewModel = hiltViewModel()
) {
    val state by viewModel.matches.collectAsState()
    val currentPlayer by viewModel.sessionManager.currentPlayer.collectAsState()
    val isAdmin = currentPlayer?.role == "admin"
    val myId by viewModel.myId.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Partidos") },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = onSportsClick) {
                            Icon(Icons.Filled.SportsSoccer, contentDescription = "Deportes")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewMatch) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo partido")
            }
        }
    ) { padding ->
        when (state) {
            is UiState.Loading -> LoadingScreen()
            is UiState.Error   -> ErrorScreen((state as UiState.Error).message)
            is UiState.Success -> {
                val list = (state as UiState.Success).data
                if (list.isEmpty()) {
                    EmptyScreen("No hay partidos programados.\n¡Creá uno!")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 8.dp,
                            start = 16.dp, end = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { it.id }) { match ->
                            MatchCard(
                                match = match,
                                myId = myId,
                                onClick = { onMatchClick(match.id) },
                                onJoin = { viewModel.joinMatch(match.id) }
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

// ─── MatchDetailScreen ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    matchId: String,
    onBack:  () -> Unit,
    viewModel: MatchDetailViewModel = hiltViewModel()
) {
    val state by viewModel.match.collectAsState()
    val canInvite by viewModel.canInvite.collectAsState()
    val invitations by viewModel.invitations.collectAsState()
    val allPlayers by viewModel.allPlayers.collectAsState()
    val availablePlayers by viewModel.availablePlayers.collectAsState()
    val inviteState by viewModel.inviteState.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val teamCount by viewModel.teamCount.collectAsState()
    val saveTeamsState by viewModel.saveTeamsState.collectAsState()
    val myId by viewModel.myId.collectAsState()
    val matchStats by viewModel.matchStats.collectAsState()
    val reportState by viewModel.reportState.collectAsState()

    var showInviteDialog by remember { mutableStateOf(false) }
    var showTeamsDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var assignments by remember { mutableStateOf<Map<String, Int?>>(emptyMap()) }

    LaunchedEffect(Unit) { viewModel.loadMatchStats() }

    val context = LocalContext.current
    LaunchedEffect(inviteState) {
        (inviteState as? UiState.Success)?.let {
            Toast.makeText(context, it.data, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(saveTeamsState) {
        (saveTeamsState as? UiState.Success)?.let {
            Toast.makeText(context, it.data, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(reportState) {
        (reportState as? UiState.Success)?.let {
            Toast.makeText(context, it.data, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del partido") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when (state) {
            is UiState.Loading -> LoadingScreen()
            is UiState.Error   -> ErrorScreen((state as UiState.Error).message)
            is UiState.Success -> {
                val match = (state as UiState.Success).data
                MatchDetailContent(
                    match          = match,
                    onStatusChange = { viewModel.updateStatus(it) },
                    canInvite      = canInvite,
                    canReport      = match.status == "finished" &&
                        (myId != null && (myId in match.participantIds || match.organizerId == myId)),
                    invitations    = invitations,
                    allPlayers     = allPlayers,
                    onInviteClick  = {
                        showInviteDialog = true
                    },
                    onTeamsClick   = {
                        val init = mutableMapOf<String, Int?>()
                        match.teams.forEachIndexed { idx, team ->
                            team.playerIds.forEach { id -> init[id] = idx }
                        }
                        assignments = init
                        showTeamsDialog = true
                    },
                    onReportClick  = { showReportDialog = true },
                    modifier       = Modifier.padding(padding)
                )
            }
            else ->{}
        }
    }

    if (showInviteDialog) {
        PlayerMultiSelectDialog(
            title = "Invitar jugadores",
            players = (availablePlayers as? UiState.Success)?.data
                ?: if (availablePlayers is UiState.Loading) null else emptyList(),
            confirmButtonText = "Enviar",
            onConfirm = { viewModel.invitePlayers(it) },
            onDismiss = { showInviteDialog = false }
        )
    }

    if (showTeamsDialog) {
        AlertDialog(
            onDismissRequest = { showTeamsDialog = false },
            title = { Text("Armar equipos") },
            text = {
                if (candidates.isEmpty()) {
                    Text("Aún no hay jugadores anotados", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                        items(candidates, key = { it }) { playerId ->
                            val name = allPlayers.firstOrNull { it.id == playerId }?.name ?: playerId
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    name,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val selected = assignments[playerId]
                                DropdownField(
                                    value = if (selected == null) "Sin equipo"
                                            else "Equipo ${selected + 1}",
                                    label = "Equipo",
                                    options = buildList {
                                        add("Sin equipo")
                                        repeat(teamCount) { i -> add("Equipo ${i + 1}") }
                                    },
                                    onOptionSelected = { itemIndex ->
                                        val teamValue: Int? = if (itemIndex == 0) null else itemIndex - 1
                                        assignments = assignments + (playerId to teamValue)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
confirmButton = {
                TextButton(
                    onClick = {
                        val teams = (0 until teamCount).map { idx ->
                            val ids = assignments.filterValues { it == idx }.keys.toList()
                            val prev = (state as? UiState.Success)?.data?.teams?.getOrNull(idx)
                            MatchTeam(name = prev?.name ?: "Equipo ${idx + 1}", playerIds = ids, score = prev?.score)
                        }
                        viewModel.saveTeams(teams)
                        showTeamsDialog = false
                    },
                    enabled = candidates.isNotEmpty()
                ) { Text("Guardar equipos") }
            },
            dismissButton = {
                TextButton(onClick = { showTeamsDialog = false }) { Text("Cancelar") }
            }
        )
    }

if (showReportDialog) {
        val match = (state as? UiState.Success)?.data
        if (match != null) {
            ReportMatchDialog(
                players = candidates,
                allPlayers = allPlayers,
                match = match,
                existingStats = matchStats,
                isSaving = reportState is UiState.Loading,
                error = (reportState as? UiState.Error)?.message,
                onConfirm = { data, teamScores ->
                    viewModel.saveReport(data, teamScores)
                },
                onDismiss = { showReportDialog = false }
            )
        }
    }
}

@Composable
private fun MatchDetailContent(
    match: Match,
    onStatusChange: (String) -> Unit,
    canInvite: Boolean,
    canReport: Boolean = false,
    invitations: UiState<List<MatchInvitation>>,
    allPlayers: List<Player>,
    onInviteClick: () -> Unit,
    onTeamsClick: () -> Unit,
    onReportClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(match.title, style = MaterialTheme.typography.displayLarge)
        StatusChip(match.status)
        Text("Duración: ${match.durationMinutes} min", style = MaterialTheme.typography.bodyMedium)
        Text("Jugadores necesarios: ${match.playersNeeded}", style = MaterialTheme.typography.bodyMedium)
        if (match.pricePerPlayer > 0) {
            Text("Costo por jugador: $${match.pricePerPlayer}", style = MaterialTheme.typography.bodyMedium)
        }
        match.description.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }

        if (canInvite) {
            OutlinedButton(onClick = onInviteClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Invitar jugadores")
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onTeamsClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Armar equipos")
            }
        }

        if (canReport) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onReportClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reportar partido")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Invitados", style = MaterialTheme.typography.titleLarge)
        when (invitations) {
            is UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> Text((invitations as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> {
                val invList = (invitations as UiState.Success).data
                if (invList.isEmpty()) {
                    Text("Aún no hay invitaciones", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    invList.forEach { inv ->
                        val name = allPlayers.firstOrNull { it.id == inv.playerId }?.name ?: inv.playerId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            StatusChip(inv.status)
                        }
                    }
                }
            }
            else -> {}
        }

        Spacer(Modifier.height(8.dp))
        Text("Equipos", style = MaterialTheme.typography.titleLarge)
        match.teams.forEachIndexed { idx, team ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(team.name.ifBlank { "Equipo ${idx + 1}" }, style = MaterialTheme.typography.titleLarge)
                        team.score?.let { Text("$it", style = MaterialTheme.typography.titleLarge) }
                    }
                    Text("${team.playerIds.size} jugadores", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Controles de estado según estado actual
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (match.status) {
                "scheduled"   -> Button(onClick = { onStatusChange("confirmed") })    { Text("Confirmar") }
                "confirmed"   -> Button(onClick = { onStatusChange("in_progress") }) { Text("Iniciar") }
                "in_progress" -> Button(onClick = { onStatusChange("finished") })    { Text("Finalizar") }
            }
            if (match.status !in listOf("finished", "cancelled")) {
                OutlinedButton(onClick = { onStatusChange("cancelled") }) { Text("Cancelar") }
            }
        }
    }
}

// ─── ReportMatchDialog ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportMatchDialog(
    players: List<String>,
    allPlayers: List<Player>,
    match: Match,
    existingStats: Map<String, MatchStat>,
    isSaving: Boolean,
    error: String?,
    onConfirm: (List<PlayerReport>, Map<Int, Int?>) -> Unit,
    onDismiss: () -> Unit
) {
    val goals = remember { mutableStateMapOf<String, String>() }
    val assists = remember { mutableStateMapOf<String, String>() }
    var ratings by remember { mutableStateOf<Map<String, Int>>(
        existingStats.mapValues { it.value.rating.toInt() }
    ) }
    val teamScores = remember { mutableStateMapOf<Int, String>() }

    fun defaultGoals(playerId: String): String =
        existingStats[playerId]?.stats?.get("goles")?.toString() ?: ""

    fun defaultAssists(playerId: String): String =
        existingStats[playerId]?.stats?.get("asistencias")?.toString() ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reportar partido") },
        text = {
            Column {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                if (match.teams.isNotEmpty()) {
                    Text("Scores de equipos", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    match.teams.forEachIndexed { idx, team ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(team.name.ifBlank { "Equipo ${idx + 1}" }, modifier = Modifier.weight(1f))
                            OutlinedTextField(
                                value = teamScores.getOrPut(idx) { team.score?.toString() ?: "" },
                                onValueChange = { teamScores[idx] = it.filter(Char::isDigit) },
                                label = { Text("Score") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(90.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(players, key = { it }) { playerId ->
                        val name = allPlayers.firstOrNull { it.id == playerId }?.name ?: playerId
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = goals[playerId] ?: defaultGoals(playerId),
                                        onValueChange = { goals[playerId] = it.filter(Char::isDigit) },
                                        label = { Text("Goles") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = assists[playerId] ?: defaultAssists(playerId),
                                        onValueChange = { assists[playerId] = it.filter(Char::isDigit) },
                                        label = { Text("Asistencias") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Puntos", modifier = Modifier.weight(1f))
                                    StarRating(
                                        value = ratings[playerId] ?: 0,
                                        onStarClick = { v ->
                                            ratings = ratings + (playerId to v)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val data = players.map { pid ->
                        PlayerReport(
                            playerId = pid,
                            goals = goals[pid]?.toIntOrNull() ?: 0,
                            assists = assists[pid]?.toIntOrNull() ?: 0,
                            rating = (ratings[pid] ?: 0).toDouble()
                        )
                    }
                    val scores = match.teams.indices.associateWith { idx ->
                        teamScores[idx]?.toIntOrNull()
                    }
                    onConfirm(data, scores)
                },
                enabled = !isSaving
            ) { Text("Guardar reporte") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ─── MatchFormScreen ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchFormScreen(
    onBack: () -> Unit,
    onManageSports: () -> Unit,
    preselectedVenueId: String = "",
    viewModel: MatchFormViewModel = hiltViewModel()
) {
    val saved by viewModel.saved.collectAsState()
    var title         by remember { mutableStateOf("") }
    var sportId       by remember { mutableStateOf("") }
    var venueId       by remember { mutableStateOf("") }
    var duration      by remember { mutableStateOf("60") }
    var playersNeeded by remember { mutableStateOf("10") }
    var price         by remember { mutableStateOf("0") }
    var dateMillis    by remember { mutableStateOf(0L) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved is UiState.Success) onBack()
    }

    val sportsState by viewModel.sports.collectAsState()
    val sportList = (sportsState as? UiState.Success)?.data ?: emptyList()

    val venuesState by viewModel.venues.collectAsState()
    val venueList = (venuesState as? UiState.Success)?.data ?: emptyList()
    val preselectedVenue by viewModel.preselectedVenue.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadSports() }

    LaunchedEffect(preselectedVenueId) {
        if (preselectedVenueId.isNotBlank()) {
            viewModel.loadVenueForMatchForm(preselectedVenueId)
        }
    }

    LaunchedEffect(preselectedVenue) {
        val v = preselectedVenue ?: return@LaunchedEffect
        if (sportId.isBlank() && venueId.isBlank()) {
            sportId = v.sportIds.firstOrNull() ?: ""
            venueId = v.id
        }
    }

    LaunchedEffect(sportId) {
        if (sportId.isNotBlank()) {
            venueId = ""
            viewModel.loadVenues(sportId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo partido") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(value = title, onValueChange = { title = it },
                label = { Text("Título del partido") }, modifier = Modifier.fillMaxWidth())

            // ─── Deporte ─────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                DropdownField(
                    value = sportList.firstOrNull { it.id == sportId }?.name ?: "",
                    label = "Deporte",
                    options = sportList.map { it.name },
                    emptyMessage = "No hay deportes",
                    onOptionSelected = { sportId = sportList[it].id },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onManageSports) {
                    Icon(Icons.Filled.Settings, contentDescription = "Gestionar deportes")
                }
            }

            // ─── Sede ────────────────────────────────────────
            DropdownField(
                value = venueList.firstOrNull { it.id == venueId }?.name ?: "",
                label = "Sede",
                options = venueList.map { it.name },
                emptyMessage = if (sportId.isBlank()) "Seleccioná un deporte primero"
                else "No hay sedes para este deporte",
                onOptionSelected = { venueId = venueList[it].id },
                modifier = Modifier.fillMaxWidth()
            )

            // ─── Fecha y hora ─────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = dateMillis.takeIf { it > 0 }?.let { formatDateTime(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = true,
                    label = { Text("Fecha y hora") },
                    placeholder = { Text("Elegir fecha y hora") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Filled.DateRange, contentDescription = "Elegir fecha")
                }
                IconButton(onClick = { showTimePicker = true }) {
                    Icon(Icons.Filled.AccessTime, contentDescription = "Elegir hora")
                }
            }

            OutlinedTextField(value = duration, onValueChange = { duration = it },
                label = { Text("Duración (min)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = playersNeeded, onValueChange = { playersNeeded = it },
                label = { Text("Jugadores necesarios") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = price, onValueChange = { price = it },
                label = { Text("Costo por jugador ($)") }, modifier = Modifier.fillMaxWidth())

            if (saved is UiState.Error) {
                Text((saved as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    viewModel.createMatch(
                        title           = title,
                        sportId         = sportId,
                        venueId         = venueId,
                        durationMinutes = duration.toIntOrNull() ?: 60,
                        playersNeeded   = playersNeeded.toIntOrNull() ?: 10,
                        pricePerPlayer  = price.toDoubleOrNull() ?: 0.0,
                        dateMillis      = dateMillis
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = saved !is UiState.Loading && dateMillis > 0
            ) {
                if (saved is UiState.Loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Crear partido")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selected = datePickerState.selectedDateMillis
                    if (selected != null) {
                        val existing = if (dateMillis > 0)
                            Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
                        else LocalDateTime.now()
                        val date = Instant.ofEpochMilli(selected)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        val ldt = LocalDateTime.of(date, existing.toLocalTime())
                        dateMillis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = if (dateMillis > 0)
                Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).hour
            else 19,
            initialMinute = if (dateMillis > 0)
                Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).minute
            else 0
        )
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val base = if (dateMillis > 0)
                        Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                    else LocalDateTime.now().plusDays(1)
                    val ldt = base.withHour(timePickerState.hour).withMinute(timePickerState.minute)
                        .withSecond(0).withNano(0)
                    dateMillis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    showTimePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            },
            title = { Text("Elegir hora") }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val ldt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val date = String.format("%02d/%02d/%d", ldt.dayOfMonth, ldt.monthValue, ldt.year)
    val time = String.format("%02d:%02d", ldt.hour, ldt.minute)
    return "$date $time"
}

// ─── MatchCard ────────────────────────────────────────────
@Composable
fun MatchCard(
    match: Match,
    myId: String?,
    onClick: () -> Unit,
    onJoin: () -> Unit,
    sportName: String? = null,
    venueName: String? = null
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(match.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                StatusChip(match.status)
            }
            val infoParts = listOfNotNull(
                sportName?.let { "Deporte: $it" },
                venueName?.let { "Sede: $it" },
                match.date?.let { "Fecha: ${formatDateTime(it.seconds * 1000)}" }
            )
            if (infoParts.isNotEmpty()) {
                Text(infoParts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            Text("${match.durationMinutes} min · ${match.playersNeeded} jugadores", style = MaterialTheme.typography.bodyMedium)
            if (match.pricePerPlayer > 0) {
                Text("$${match.pricePerPlayer} por jugador", style = MaterialTheme.typography.bodyMedium)
            }
            val isOpen = match.status == "scheduled" && match.playersNeeded > 0
            val isOrganizer = myId != null && match.organizerId == myId
            val isJoined = myId != null && myId in match.participantIds
            if (isOpen && !isOrganizer) {
                Spacer(Modifier.height(4.dp))
                if (isJoined) {
                    Text("Ya estás anotado", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                } else {
                    Button(onClick = onJoin, modifier = Modifier.fillMaxWidth()) { Text("Anotarme") }
                }
            }
        }
    }
}

@Composable
fun PlayerMultiSelectDialog(
    title: String,
    players: List<Player>?,
    confirmButtonText: String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            when {
                players == null -> CircularProgressIndicator()
                players.isEmpty() ->
                    Text("No hay jugadores disponibles", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(players, key = { it.id }) { player ->
                            ListItem(
                                headlineContent  = { Text(player.name) },
                                supportingContent = { Text(player.position.ifBlank { "Sin posición" }) },
                                trailingContent = {
                                    Checkbox(
                                        checked = player.id in selected,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) selected + player.id else selected - player.id
                                        }
                                    )
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selected.toList())
                    onDismiss()
                },
                enabled = selected.isNotEmpty() && players != null
            ) { Text(confirmButtonText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
