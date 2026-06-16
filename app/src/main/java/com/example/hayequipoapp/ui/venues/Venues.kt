package com.example.hayequipoapp.ui.venues

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.example.hayequipoapp.domain.repository.VenueRepository
import com.example.hayequipoapp.ui.common.EmptyScreen
import com.example.hayequipoapp.ui.common.ErrorScreen
import com.example.hayequipoapp.ui.common.LoadingScreen
import com.example.hayequipoapp.ui.common.SectionHeader
import com.example.hayequipoapp.ui.common.UiState
import com.example.hayequipoapp.data.model.Venue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


// ─── List ViewModel ───────────────────────────────────────
@HiltViewModel
class VenueListViewModel @Inject constructor(
    private val venueRepository: VenueRepository
) : ViewModel() {

    private val _venues = MutableStateFlow<UiState<List<Venue>>>(UiState.Loading)
    val venues = _venues.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            venueRepository.getVenues().collect {
                _venues.value = UiState.Success(it)
            }
        }
    }

    fun deleteVenue(venueId: String) {
        viewModelScope.launch {
            venueRepository.deleteVenue(venueId)
        }
    }
}


// ─── Detail ViewModel ─────────────────────────────────────
@HiltViewModel
class VenueDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val venueRepository: VenueRepository
) : ViewModel() {

    private val venueId: String = checkNotNull(savedStateHandle["venueId"])

    private val _venue = MutableStateFlow<UiState<Venue>>(UiState.Loading)
    val venue = _venue.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val v = venueRepository.getVenueById(venueId)
                _venue.value = if (v != null) UiState.Success(v) else UiState.Error("Sede no encontrada")
            } catch (e: Exception) {
                _venue.value = UiState.Error(e.message ?: "Error")
            }
        }
    }

    fun deleteVenue() {
        viewModelScope.launch {
            venueRepository.deleteVenue(venueId)
            _venue.value = UiState.Error("Eliminada") // señal para navegar atrás
        }
    }
}


// ─── Form ViewModel ───────────────────────────────────────
@HiltViewModel
class VenueFormViewModel @Inject constructor(
    private val venueRepository: VenueRepository
) : ViewModel() {

    var name by mutableStateOf("")
    var address by mutableStateOf("")
    var phone by mutableStateOf("")
    var pricePerHour by mutableStateOf("0")
    var amenitiesText by mutableStateOf("")
    var sportIdsText by mutableStateOf("")

    private val _isEditing = MutableStateFlow(false)
    val isEditing = _isEditing.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _saveState = MutableStateFlow<UiState<String>?>(null)
    val saveState = _saveState.asStateFlow()

    fun loadVenue(venueId: String) {
        if (venueId.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _isEditing.value = true
            try {
                val venue = venueRepository.getVenueById(venueId)
                if (venue != null) {
                    name = venue.name
                    address = venue.address
                    phone = venue.phone
                    pricePerHour = if (venue.pricePerHour > 0) venue.pricePerHour.toString() else "0"
                    amenitiesText = venue.amenities.joinToString(", ")
                    sportIdsText = venue.sportIds.joinToString(", ")
                }
            } catch (_: Exception) { }
            _isLoading.value = false
        }
    }

    fun save(venueId: String) {
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            val venue = Venue(
                id = venueId,
                name = name.trim(),
                address = address.trim(),
                phone = phone.trim(),
                pricePerHour = pricePerHour.toDoubleOrNull() ?: 0.0,
                amenities = amenitiesText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                sportIds = sportIdsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
            )
            val result = if (venueId.isBlank()) {
                venueRepository.createVenue(venue)
            } else {
                venueRepository.updateVenue(venue)
            }
            result.fold(
                onSuccess = { id -> _saveState.value = UiState.Success(id.toString()) },
                onFailure = { e -> _saveState.value = UiState.Error(e.message ?: "Error al guardar") }
            )
        }
    }
}


// ─── VenueListScreen ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VenueListScreen(
    onVenueClick: (String) -> Unit,
    onNewVenue:   () -> Unit,
    viewModel: VenueListViewModel = hiltViewModel()
) {
    val state by viewModel.venues.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sedes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewVenue) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva sede")
            }
        }
    ) { padding ->
        when (state) {
            is UiState.Loading -> LoadingScreen()
            is UiState.Error   -> ErrorScreen((state as UiState.Error).message)
            is UiState.Success -> {
                val list = (state as UiState.Success).data
                if (list.isEmpty()) {
                    EmptyScreen("No hay sedes registradas.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateTopPadding(),
                            start = 16.dp, end = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { it.id }) { venue ->
                            VenueCard(
                                venue = venue,
                                onClick = { onVenueClick(venue.id) },
                                onDelete = { viewModel.deleteVenue(venue.id) }
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}


// ─── VenueDetailScreen ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VenueDetailScreen(
    venueId: String,
    onBack:  () -> Unit,
    onEdit:  () -> Unit,
    viewModel: VenueDetailViewModel = hiltViewModel()
) {
    val state by viewModel.venue.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is UiState.Error && (state as UiState.Error).message == "Eliminada") {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de sede") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        when (state) {
            is UiState.Loading -> LoadingScreen()
            is UiState.Error -> {
                val msg = (state as UiState.Error).message
                if (msg != "Eliminada") ErrorScreen(msg)
            }
            is UiState.Success -> {
                val venue = (state as UiState.Success).data
                VenueDetailContent(venue = venue, modifier = Modifier.padding(padding))
            }
            else -> {}
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar sede") },
            text = { Text("¿Eliminar esta sede permanentemente?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteVenue()
                    showDeleteDialog = false
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}


// ─── VenueFormScreen ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VenueFormScreen(
    venueId: String,
    onBack:  () -> Unit,
    viewModel: VenueFormViewModel = hiltViewModel()
) {
    val isEditing by viewModel.isEditing.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val saveState by viewModel.saveState.collectAsState()

    LaunchedEffect(venueId) {
        viewModel.loadVenue(venueId)
    }

    LaunchedEffect(saveState) {
        if (saveState is UiState.Success) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar sede" else "Nueva sede") },
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
                    value = viewModel.address,
                    onValueChange = { viewModel.address = it },
                    label = { Text("Dirección *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.phone,
                    onValueChange = { viewModel.phone = it },
                    label = { Text("Teléfono") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.pricePerHour,
                    onValueChange = { viewModel.pricePerHour = it },
                    label = { Text("Precio por hora ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.amenitiesText,
                    onValueChange = { viewModel.amenitiesText = it },
                    label = { Text("Servicios (separados por coma)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.sportIdsText,
                    onValueChange = { viewModel.sportIdsText = it },
                    label = { Text("IDs de deportes (separados por coma)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                val errorMsg = (saveState as? UiState.Error)?.message
                if (errorMsg != null) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.save(venueId) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = saveState !is UiState.Loading && viewModel.name.isNotBlank()
                            && viewModel.address.isNotBlank()
                ) {
                    if (saveState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isEditing) "Guardar cambios" else "Crear sede")
                }
            }
        }
    }
}


// ─── VenueDetailContent ───────────────────────────────────
@Composable
private fun VenueDetailContent(venue: Venue, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(venue.name, style = MaterialTheme.typography.displayLarge)
        Text(venue.address, style = MaterialTheme.typography.bodyMedium)
        if (venue.phone.isNotBlank()) {
            Text("Tel: ${venue.phone}", style = MaterialTheme.typography.bodyMedium)
        }
        if (venue.pricePerHour > 0) {
            Text("$${venue.pricePerHour}/hora", style = MaterialTheme.typography.bodyLarge)
        }
        if (venue.rating > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(String.format("%.1f", venue.rating), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (venue.amenities.isNotEmpty()) {
            SectionHeader("Servicios")
            Text(venue.amenities.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
        if (venue.schedules.isNotEmpty()) {
            SectionHeader("Horarios")
            val days = mapOf(
                "monday" to "Lunes", "tuesday" to "Martes", "wednesday" to "Miércoles",
                "thursday" to "Jueves", "friday" to "Viernes",
                "saturday" to "Sábado", "sunday" to "Domingo"
            )
            days.forEach { (key, label) ->
                venue.schedules[key]?.let { day ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (day.isOpen) "${day.open} - ${day.close}" else "Cerrado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (day.isOpen) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}


// ─── VenueCard ────────────────────────────────────────────
@Composable
fun VenueCard(
    venue: Venue,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(venue.name, style = MaterialTheme.typography.titleLarge)
                Text(venue.address, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (venue.rating > 0) Text("★ ${String.format("%.1f", venue.rating)}", style = MaterialTheme.typography.labelSmall)
                    if (venue.pricePerHour > 0) Text("$${venue.pricePerHour}/h", style = MaterialTheme.typography.labelSmall)
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
            title = { Text("Eliminar sede") },
            text = { Text("¿Eliminar \"${venue.name}\"?") },
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

