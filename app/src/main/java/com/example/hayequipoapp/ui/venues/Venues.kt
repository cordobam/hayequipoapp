package com.example.hayequipoapp.ui.venues

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
}

// ─── VenueListScreen ──────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VenueListScreen(
    onVenueClick: (String) -> Unit,
    viewModel: VenueListViewModel = hiltViewModel()
) {
    val state by viewModel.venues.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sedes") }) }
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
                            bottom = padding.calculateBottomPadding() + 8.dp,
                            start = 16.dp, end = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { it.id }) { venue ->
                            VenueCard(venue = venue, onClick = { onVenueClick(venue.id) })
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
    viewModel: VenueDetailViewModel = hiltViewModel()
) {
    val state by viewModel.venue.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de sede") },
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
                val venue = (state as UiState.Success).data
                VenueDetailContent(venue = venue, modifier = Modifier.padding(padding))
            }
            else -> {}
        }
    }
}

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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(String.format("%.1f", venue.rating), style = MaterialTheme.typography.bodyMedium)
        }
        if (venue.pricePerHour > 0) {
            Text("$${venue.pricePerHour}/hora", style = MaterialTheme.typography.bodyLarge)
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
fun VenueCard(venue: Venue, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(venue.name, style = MaterialTheme.typography.titleLarge)
            Text(venue.address, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (venue.rating > 0) Text("★ ${String.format("%.1f", venue.rating)}", style = MaterialTheme.typography.labelSmall)
                if (venue.pricePerHour > 0) Text("$${venue.pricePerHour}/h", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
