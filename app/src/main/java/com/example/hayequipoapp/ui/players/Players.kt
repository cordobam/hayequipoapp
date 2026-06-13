package com.example.hayequipoapp.ui.players

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.hayequipoapp.domain.repository.PlayerRepository
import com.example.hayequipoapp.domain.repository.PlayerReviewRepository
import com.example.hayequipoapp.domain.repository.PlayerStatRepository
import com.example.hayequipoapp.ui.common.EmptyScreen
import com.example.hayequipoapp.ui.common.ErrorScreen
import com.example.hayequipoapp.ui.common.LoadingScreen
import com.example.hayequipoapp.ui.common.SectionHeader
import com.example.hayequipoapp.ui.common.StarRating
import com.example.hayequipoapp.ui.common.UiState
import com.example.hayequipoapp.data.model.Player
import com.example.hayequipoapp.data.model.PlayerReview
import com.example.hayequipoapp.data.model.PlayerStat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── List ViewModel ───────────────────────────────────────
@HiltViewModel
class PlayerListViewModel @Inject constructor(
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val _players = MutableStateFlow<UiState<List<Player>>>(UiState.Loading)
    val players = _players.asStateFlow()

    var searchQuery by mutableStateOf("")
        private set

    init { load() }

    fun onSearchQueryChange(q: String) { searchQuery = q }

    fun load() {
        viewModelScope.launch {
            playerRepository.getPlayers().collect { list ->
                _players.value = UiState.Success(list)
            }
        }
    }
}

// ─── Profile ViewModel ────────────────────────────────────
@HiltViewModel
class PlayerProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playerRepository: PlayerRepository,
    private val reviewRepository: PlayerReviewRepository,
    private val statRepository: PlayerStatRepository
) : ViewModel() {

    private val playerId: String = checkNotNull(savedStateHandle["playerId"])

    private val _player  = MutableStateFlow<UiState<Player>>(UiState.Loading)
    val player  = _player.asStateFlow()

    private val _reviews = MutableStateFlow<UiState<List<PlayerReview>>>(UiState.Loading)
    val reviews = _reviews.asStateFlow()

    private val _stats   = MutableStateFlow<List<PlayerStat>>(emptyList())
    val stats = _stats.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val p = playerRepository.getPlayerById(playerId)
                _player.value = if (p != null) UiState.Success(p) else UiState.Error("Jugador no encontrado")
            } catch (e: Exception) {
                _player.value = UiState.Error(e.message ?: "Error")
            }
        }
        viewModelScope.launch {
            reviewRepository.getReviewsForPlayer(playerId).collect {
                _reviews.value = UiState.Success(it)
            }
        }
        viewModelScope.launch {
            statRepository.getAllStatsForPlayer(playerId).collect {
                _stats.value = it
            }
        }
    }
}

// ─── PlayerListScreen ─────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerListScreen(
    onPlayerClick: (String) -> Unit,
    viewModel: PlayerListViewModel = hiltViewModel()
) {
    val state by viewModel.players.collectAsState()
    val query = viewModel.searchQuery

    Scaffold(
        topBar = { TopAppBar(title = { Text("Jugadores") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value         = query,
                onValueChange = viewModel::onSearchQueryChange,
                label         = { Text("Buscar jugador") },
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            when (state) {
                is UiState.Loading -> LoadingScreen()
                is UiState.Error   -> ErrorScreen((state as UiState.Error).message)
                is UiState.Success -> {
                    val list = (state as UiState.Success).data
                        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                    if (list.isEmpty()) {
                        EmptyScreen("No se encontraron jugadores.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(list, key = { it.id }) { player ->
                                PlayerCard(player = player, onClick = { onPlayerClick(player.id) })
                            }
                        }
                    }
                }
                else ->{}
            }
        }
    }
}

// ─── PlayerProfileScreen ─────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProfileScreen(
    playerId: String,
    onBack:   () -> Unit,
    viewModel: PlayerProfileViewModel = hiltViewModel()
) {
    val playerState  by viewModel.player.collectAsState()
    val reviewsState by viewModel.reviews.collectAsState()
    val stats        by viewModel.stats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when (playerState) {
            is UiState.Loading -> LoadingScreen()
            is UiState.Error   -> ErrorScreen((playerState as UiState.Error).message)
            is UiState.Success -> {
                val player = (playerState as UiState.Success).data
                LazyColumn(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { PlayerHeader(player) }
                    item {
                        if (stats.isNotEmpty()) {
                            StatsSection(stats)
                        }
                    }
                    if (player.showReviews) {
                        item { SectionHeader("Reseñas") }
                        when (reviewsState) {
                            is UiState.Success -> {
                                val reviews = (reviewsState as UiState.Success).data
                                if (reviews.isEmpty()) {
                                    item { Text("Sin reseñas aún", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                } else {
                                    items(reviews, key = { it.id }) { review ->
                                        ReviewCard(review)
                                    }
                                }
                            }
                            else -> item { CircularProgressIndicator() }
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun PlayerHeader(player: Player) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (player.photoUrl.isNotBlank()) {
            AsyncImage(
                model = player.photoUrl,
                contentDescription = player.name,
                modifier = Modifier.size(72.dp)
            )
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(72.dp))
        }
        Column {
            Text(player.name, style = MaterialTheme.typography.headlineMedium)
            Text(player.position.ifBlank { "Sin posición" }, style = MaterialTheme.typography.bodyMedium)
            StarRating(value = player.skillLevel)
            if (player.isAvailable) {
                Text("Disponible", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StatsSection(stats: List<PlayerStat>) {
    SectionHeader("Estadísticas")
    stats.forEach { stat ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem("Partidos", stat.matchesPlayed.toString())
                StatItem("Ganados",  stat.matchesWon.toString())
                StatItem("Goles",    stat.goals.toString())
                StatItem("Assists",  stat.assists.toString())
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReviewCard(review: PlayerReview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("Confiabilidad", style = MaterialTheme.typography.labelSmall)
                    StarRating(value = review.reliability)
                }
                Column {
                    Text("Nivel", style = MaterialTheme.typography.labelSmall)
                    StarRating(value = review.skill)
                }
                Column {
                    Text("Actitud", style = MaterialTheme.typography.labelSmall)
                    StarRating(value = review.attitude)
                }
            }
            if (review.comment.isNotBlank()) {
                Text(review.comment, style = MaterialTheme.typography.bodyMedium)
            }
            if (review.wouldPlayAgain) {
                Text("✓ Volvería a jugar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ─── PlayerCard ───────────────────────────────────────────
@Composable
fun PlayerCard(player: Player, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (player.photoUrl.isNotBlank()) {
                AsyncImage(model = player.photoUrl, contentDescription = player.name, modifier = Modifier.size(48.dp))
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(48.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(player.name, style = MaterialTheme.typography.titleLarge)
                Text(player.position.ifBlank { "Sin posición" }, style = MaterialTheme.typography.bodyMedium)
                StarRating(value = player.skillLevel)
            }
            if (player.isAvailable) {
                Text("Disponible", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
