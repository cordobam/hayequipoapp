package com.example.hayequipoapp.ui.sports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.example.hayequipoapp.domain.repository.SportRepository
import com.example.hayequipoapp.ui.common.EmptyScreen
import com.example.hayequipoapp.ui.common.ErrorScreen
import com.example.hayequipoapp.ui.common.LoadingScreen
import com.example.hayequipoapp.ui.common.UiState
import com.example.hayequipoapp.data.model.Sport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


// ─── List ViewModel ───────────────────────────────────────
@HiltViewModel
class SportListViewModel @Inject constructor(
    private val sportRepository: SportRepository
) : ViewModel() {

    private val _sports = MutableStateFlow<UiState<List<Sport>>>(UiState.Loading)
    val sports = _sports.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            sportRepository.getSports().collect {
                _sports.value = UiState.Success(it)
            }
        }
    }

    fun deleteSport(sportId: String) {
        viewModelScope.launch {
            sportRepository.deleteSport(sportId)
        }
    }
}


// ─── Form ViewModel ───────────────────────────────────────
@HiltViewModel
class SportFormViewModel @Inject constructor(
    private val sportRepository: SportRepository
) : ViewModel() {

    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var type by mutableStateOf("team")
    var playersPerTeam by mutableStateOf("5")
    var minPlayersPerTeam by mutableStateOf("4")
    var maxPlayersPerTeam by mutableStateOf("7")
    var teamCount by mutableStateOf("2")
    var scoringUnit by mutableStateOf("goles")
    var durationMinutes by mutableStateOf("40")
    var active by mutableStateOf(true)

    private val _isEditing = MutableStateFlow(false)
    val isEditing = _isEditing.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _saveState = MutableStateFlow<UiState<String>?>(null)
    val saveState = _saveState.asStateFlow()

    fun loadSport(sportId: String) {
        if (sportId.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _isEditing.value = true
            try {
                val sport = sportRepository.getSportById(sportId)
                if (sport != null) {
                    name = sport.name
                    description = sport.description
                    type = sport.type
                    playersPerTeam = sport.playersPerTeam.toString()
                    minPlayersPerTeam = sport.minPlayersPerTeam.toString()
                    maxPlayersPerTeam = sport.maxPlayersPerTeam.toString()
                    teamCount = sport.teamCount.toString()
                    scoringUnit = sport.scoringUnit
                    durationMinutes = sport.durationMinutes.toString()
                    active = sport.active
                }
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun save(sportId: String) {
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            val sport = Sport(
                id = sportId,
                name = name.trim(),
                description = description.trim(),
                type = type,
                playersPerTeam = playersPerTeam.toIntOrNull() ?: 5,
                minPlayersPerTeam = minPlayersPerTeam.toIntOrNull() ?: 4,
                maxPlayersPerTeam = maxPlayersPerTeam.toIntOrNull() ?: 7,
                teamCount = teamCount.toIntOrNull() ?: 2,
                scoringUnit = scoringUnit,
                durationMinutes = durationMinutes.toIntOrNull() ?: 40,
                active = active
            )
            val result = if (sportId.isBlank()) {
                sportRepository.createSport(sport)
            } else {
                sportRepository.updateSport(sport)
            }
            result.fold(
                onSuccess = { id -> _saveState.value = UiState.Success(id.toString()) },
                onFailure = { e -> _saveState.value = UiState.Error(e.message ?: "Error al guardar") }
            )
        }
    }
}


// ─── List Screen ──────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportListScreen(
    navController: NavController,
    viewModel: SportListViewModel = hiltViewModel()
) {
    val state by viewModel.sports.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Deportes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate("sports/form")
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo deporte")
            }
        }
    ) { padding ->
        when (state) {
            is UiState.Loading -> LoadingScreen()
            is UiState.Error   -> ErrorScreen((state as UiState.Error).message)
            is UiState.Success -> {
                val list = (state as UiState.Success).data
                if (list.isEmpty()) {
                    EmptyScreen("No hay deportes disponibles.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = 88.dp, start = 16.dp, end = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { it.id }) { sport ->
                            SportCard(
                                sport = sport,
                                onEdit = { navController.navigate("sports/form?sportId=${sport.id}") },
                                onDelete = { viewModel.deleteSport(sport.id) }
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}


// ─── Sport Card ───────────────────────────────────────────
@Composable
private fun SportCard(
    sport: Sport,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(sport.name, style = MaterialTheme.typography.titleLarge)
                Text(sport.description, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${sport.playersPerTeam}v${sport.playersPerTeam}", style = MaterialTheme.typography.labelSmall)
                    Text("${sport.durationMinutes} min", style = MaterialTheme.typography.labelSmall)
                    Text("Anota en ${sport.scoringUnit}", style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar deporte") },
            text = { Text("¿Eliminar \"${sport.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}


// ─── Form Screen ──────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportFormScreen(
    sportId: String,
    onBack: () -> Unit,
    viewModel: SportFormViewModel = hiltViewModel()
) {
    val isEditing by viewModel.isEditing.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val saveState by viewModel.saveState.collectAsState()

    LaunchedEffect(sportId) {
        viewModel.loadSport(sportId)
    }

    LaunchedEffect(saveState) {
        if (saveState is UiState.Success) onBack()
    }

    val typeOptions = listOf("team" to "Equipo", "individual" to "Individual", "pairs" to "Parejas")
    val unitOptions = listOf("goles" to "Goles", "puntos" to "Puntos", "sets" to "Sets")

    var typeExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar deporte" else "Nuevo deporte") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Cancelar") }
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
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.description,
                    onValueChange = { viewModel.description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 4
                )

                // Tipo
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = typeOptions.first { it.first == viewModel.type }.second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        typeOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { viewModel.type = key; typeExpanded = false }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = viewModel.playersPerTeam,
                        onValueChange = { viewModel.playersPerTeam = it },
                        label = { Text("Jugadores x equipo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = viewModel.minPlayersPerTeam,
                        onValueChange = { viewModel.minPlayersPerTeam = it },
                        label = { Text("Mínimo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = viewModel.maxPlayersPerTeam,
                        onValueChange = { viewModel.maxPlayersPerTeam = it },
                        label = { Text("Máximo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = viewModel.teamCount,
                    onValueChange = { viewModel.teamCount = it },
                    label = { Text("Cantidad de equipos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.durationMinutes,
                    onValueChange = { viewModel.durationMinutes = it },
                    label = { Text("Duración (minutos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Unidad de puntuación
                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = it }
                ) {
                    OutlinedTextField(
                        value = unitOptions.first { it.first == viewModel.scoringUnit }.second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unidad de puntuación") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                        unitOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { viewModel.scoringUnit = key; unitExpanded = false }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Activo", modifier = Modifier.weight(1f))
                    Switch(checked = viewModel.active, onCheckedChange = { viewModel.active = it })
                }

                Spacer(Modifier.height(8.dp))

                val errorMsg = (saveState as? UiState.Error)?.message
                if (errorMsg != null) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = { viewModel.save(sportId) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = saveState !is UiState.Loading && viewModel.name.isNotBlank()
                ) {
                    if (saveState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isEditing) "Guardar cambios" else "Crear deporte")
                }
            }
        }
    }
}
