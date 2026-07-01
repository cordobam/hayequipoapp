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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.ui.text.input.KeyboardType
import com.example.hayequipoapp.data.session.SessionManager

// ─── List ViewModel ───────────────────────────────────────
@HiltViewModel
class PlayerListViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val sessionManager: SessionManager
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
                _players.value = UiState.Success(
                    list.filter { it.id != sessionManager.currentPlayerId }
                )
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
    private val statRepository: PlayerStatRepository,
    val sessionManager: SessionManager
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

    fun deletePlayer() {
        viewModelScope.launch {
            playerRepository.deletePlayer(playerId)
            _player.value = UiState.Error("Eliminado")
        }
    }
}
// ─── Edit ViewModel ───────────────────────────────────────
@HiltViewModel
class EditPlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository
) : ViewModel() {

    var name by mutableStateOf("")
    var phone by mutableStateOf("")
    var photoUrl by mutableStateOf("")
    var position by mutableStateOf("")
    var skillLevel by mutableStateOf("3")
    var isAvailable by mutableStateOf(true)
    var showReviews by mutableStateOf(true)
    var preferredSportsText by mutableStateOf("")

    private val _isEditing = MutableStateFlow(false)
    val isEditing = _isEditing.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _saveState = MutableStateFlow<UiState<String>?>(null)
    val saveState = _saveState.asStateFlow()

    fun loadPlayer(playerId: String) {
        if (playerId.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _isEditing.value = true
            try {
                val player = playerRepository.getPlayerById(playerId)
                if (player != null) {
                    name = player.name
                    phone = player.phone
                    photoUrl = player.photoUrl
                    position = player.position
                    skillLevel = player.skillLevel.toString()
                    isAvailable = player.isAvailable
                    showReviews = player.showReviews
                    preferredSportsText = player.preferredSports.joinToString(", ")
                }
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun save(playerId: String) {
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            val existing = playerRepository.getPlayerById(playerId)
            if (existing == null) {
                _saveState.value = UiState.Error("Jugador no encontrado")
                return@launch
            }
            val player = existing.copy(
                name = name.trim(),
                phone = phone.trim(),
                photoUrl = photoUrl.trim(),
                position = position.trim(),
                skillLevel = skillLevel.toIntOrNull() ?: 3,
                isAvailable = isAvailable,
                showReviews = showReviews,
                preferredSports = preferredSportsText.split(",")
                    .map { it.trim() }.filter { it.isNotBlank() }
            )
            val result = playerRepository.updatePlayer(player)
            result.fold(
                onSuccess = { _saveState.value = UiState.Success(playerId) },
                onFailure = { e -> _saveState.value = UiState.Error(e.message ?: "Error al guardar") }
            )
        }
    }
}


// ─── PlayerListScreen ─────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerListScreen(
    onPlayerClick: (String) -> Unit,
    onProfileClick: () -> Unit = {},
    viewModel: PlayerListViewModel = hiltViewModel()
) {
    val state by viewModel.players.collectAsState()
    val query = viewModel.searchQuery

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jugadores") },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Filled.Person, contentDescription = "Mi perfil")
                    }
                }
            )
        }
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
    onEdit:   () -> Unit,
    viewModel: PlayerProfileViewModel = hiltViewModel()
) {
    val playerState  by viewModel.player.collectAsState()
    val reviewsState by viewModel.reviews.collectAsState()
    val stats        by viewModel.stats.collectAsState()
    val currentPlayer by viewModel.sessionManager.currentPlayer.collectAsState()
    val isOwnProfile = currentPlayer?.id == playerId
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(playerState) {
        if (playerState is UiState.Error && (playerState as UiState.Error).message == "Eliminado") {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (isOwnProfile) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (playerState) {
            is UiState.Loading -> LoadingScreen()
            is UiState.Error -> {
                val msg = (playerState as UiState.Error).message
                if (msg != "Eliminado") ErrorScreen(msg)
            }
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
                    if (isOwnProfile) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Editar perfil")
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Eliminar cuenta")
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar cuenta") },
            text = { Text("¿Eliminar esta cuenta permanentemente?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlayer()
                    showDeleteDialog = false
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
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

// ─── EditPlayerScreen ─────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlayerScreen(
    playerId: String,
    onBack: () -> Unit,
    viewModel: EditPlayerViewModel = hiltViewModel()
) {
    val isEditing by viewModel.isEditing.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val saveState by viewModel.saveState.collectAsState()

    LaunchedEffect(playerId) {
        viewModel.loadPlayer(playerId)
    }

    LaunchedEffect(saveState) {
        if (saveState is UiState.Success) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar perfil" else "Nuevo jugador") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            LoadingScreen()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it },
                    label = { Text("Nombre *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.phone,
                    onValueChange = { viewModel.phone = it },
                    label = { Text("Teléfono") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.photoUrl,
                    onValueChange = { viewModel.photoUrl = it },
                    label = { Text("URL de foto") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.position,
                    onValueChange = { viewModel.position = it },
                    label = { Text("Posición") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.skillLevel,
                    onValueChange = { viewModel.skillLevel = it },
                    label = { Text("Nivel (1-5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.preferredSportsText,
                    onValueChange = { viewModel.preferredSportsText = it },
                    label = { Text("Deportes favoritos (separados por coma)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Disponible", modifier = Modifier.weight(1f))
                    Switch(checked = viewModel.isAvailable, onCheckedChange = { viewModel.isAvailable = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mostrar reseñas", modifier = Modifier.weight(1f))
                    Switch(checked = viewModel.showReviews, onCheckedChange = { viewModel.showReviews = it })
                }

                val errorMsg = (saveState as? UiState.Error)?.message
                if (errorMsg != null) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.save(playerId) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = saveState !is UiState.Loading && viewModel.name.isNotBlank()
                ) {
                    if (saveState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isEditing) "Guardar cambios" else "Crear jugador")
                }
            }
        }
    }
}
