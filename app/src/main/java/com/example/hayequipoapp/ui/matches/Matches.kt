package com.example.hayequipoapp.ui.matches

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hayequipoapp.domain.repository.MatchRepository
import com.example.hayequipoapp.ui.common.EmptyScreen
import com.example.hayequipoapp.ui.common.ErrorScreen
import com.example.hayequipoapp.ui.common.LoadingScreen
import com.example.hayequipoapp.ui.common.StatusChip
import com.example.hayequipoapp.ui.common.UiState
import com.google.firebase.Timestamp
import com.example.hayequipoapp.data.model.Match
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.example.hayequipoapp.domain.repository.SportRepository
import com.example.hayequipoapp.data.model.Sport
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment

// ─── List ViewModel ───────────────────────────────────────
@HiltViewModel
class MatchListViewModel @Inject constructor(
    private val matchRepository: MatchRepository
) : ViewModel() {

    private val _matches = MutableStateFlow<UiState<List<Match>>>(UiState.Loading)
    val matches = _matches.asStateFlow()

    init { loadMatches() }

    fun loadMatches() {
        viewModelScope.launch {
            matchRepository.getUpcomingMatches().collect {
                _matches.value = UiState.Success(it)
            }
        }
    }
}

// ─── Detail ViewModel ─────────────────────────────────────
@HiltViewModel
class MatchDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val matchRepository: MatchRepository
) : ViewModel() {

    private val matchId: String = checkNotNull(savedStateHandle["matchId"])

    private val _match = MutableStateFlow<UiState<Match>>(UiState.Loading)
    val match = _match.asStateFlow()

    init { loadMatch() }

    fun loadMatch() {
        viewModelScope.launch {
            try {
                val m = matchRepository.getMatchById(matchId)
                _match.value = if (m != null) UiState.Success(m) else UiState.Error("Partido no encontrado")
            } catch (e: Exception) {
                _match.value = UiState.Error(e.message ?: "Error al cargar partido")
            }
        }
    }

    fun updateStatus(status: String) {
        viewModelScope.launch {
            matchRepository.updateMatchStatus(matchId, status)
        }
    }
}

// ─── Form ViewModel ───────────────────────────────────────

@HiltViewModel
class MatchFormViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
    private val auth: FirebaseAuth,
    private val sportRepository: SportRepository
) : ViewModel() {

    private val _saved = MutableStateFlow<UiState<String>?>(null)
    val saved = _saved.asStateFlow()

    private val _sports = MutableStateFlow<UiState<List<Sport>>>(UiState.Loading)
    val sports = _sports.asStateFlow()

    fun loadSports() {                                                                 // ← NUEVO
        viewModelScope.launch {
            sportRepository.getSports().collect {
                _sports.value = UiState.Success(it)
            }
        }
    }

    fun createMatch(
        title: String,
        sportId: String,
        venueId: String,
        durationMinutes: Int,
        playersNeeded: Int,
        pricePerPlayer: Double
    ) {
        val organizerId = auth.currentUser?.uid
            ?: run { _saved.value = UiState.Error("No hay sesión activa"); return }

        viewModelScope.launch {
            _saved.value = UiState.Loading
            val match = Match(
                title           = title,
                sportId         = sportId,
                venueId         = venueId,
                organizerId     = organizerId,   // ← viene de FirebaseAuth
                durationMinutes = durationMinutes,
                playersNeeded   = playersNeeded,
                pricePerPlayer  = pricePerPlayer,
                date            = Timestamp.now()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Partidos") },
                actions = {
                    IconButton(onClick = onSportsClick) {
                        Icon(Icons.Filled.SportsSoccer, contentDescription = "Deportes")
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
                            MatchCard(match = match, onClick = { onMatchClick(match.id) })
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
                    match     = match,
                    onStatusChange = { viewModel.updateStatus(it) },
                    modifier  = Modifier.padding(padding)
                )
            }
            else ->{}
        }
    }
}

@Composable
private fun MatchDetailContent(
    match: Match,
    onStatusChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

// ─── MatchFormScreen ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchFormScreen(
    onBack: () -> Unit,
    onManageSports: () -> Unit,
    viewModel: MatchFormViewModel = hiltViewModel()
) {
    val saved by viewModel.saved.collectAsState()
    var title         by remember { mutableStateOf("") }
    var sportId       by remember { mutableStateOf("") }
    var venueId       by remember { mutableStateOf("") }
    var duration      by remember { mutableStateOf("60") }
    var playersNeeded by remember { mutableStateOf("10") }
    var price         by remember { mutableStateOf("0") }
    var sportExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved is UiState.Success) onBack()
    }

    val sportsState by viewModel.sports.collectAsState()
    val sportList = (sportsState as? UiState.Success)?.data ?: emptyList()

    LaunchedEffect(Unit) { viewModel.loadSports() }

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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(
                    expanded = sportExpanded,
                    onExpandedChange = { sportExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = sportList.firstOrNull { it.id == sportId }?.name
                            ?: if (sportId.isBlank()) "" else sportId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Deporte") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sportExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = sportExpanded, onDismissRequest = { sportExpanded = false }) {
                        if (sportList.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No hay deportes") },
                                onClick = { sportExpanded = false }
                            )
                        } else {
                            sportList.forEach { sport ->
                                DropdownMenuItem(
                                    text = { Text(sport.name) },
                                    onClick = { sportId = sport.id; sportExpanded = false }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onManageSports) {
                    Icon(Icons.Filled.Settings, contentDescription = "Gestionar deportes")
                }
            }
            OutlinedTextField(value = venueId, onValueChange = { venueId = it },
                label = { Text("ID de la sede") }, modifier = Modifier.fillMaxWidth())
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
                    viewModel.createMatch(   // ← ya no pide organizerId
                        title           = title,
                        sportId         = sportId,
                        venueId         = venueId,
                        durationMinutes = duration.toIntOrNull() ?: 60,
                        playersNeeded   = playersNeeded.toIntOrNull() ?: 10,
                        pricePerPlayer  = price.toDoubleOrNull() ?: 0.0
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = saved !is UiState.Loading
            ) {
                if (saved is UiState.Loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Crear partido")
            }
        }
    }
}

// ─── MatchCard ────────────────────────────────────────────
@Composable
fun MatchCard(match: Match, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(match.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                StatusChip(match.status)
            }
            Text("${match.durationMinutes} min · ${match.playersNeeded} jugadores", style = MaterialTheme.typography.bodyMedium)
            if (match.pricePerPlayer > 0) {
                Text("$${match.pricePerPlayer} por jugador", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
