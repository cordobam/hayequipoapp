package com.example.hayequipoapp.ui.sports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportListScreen(
    navController: NavController,
    viewModel: SportListViewModel = hiltViewModel()
) {
    val state by viewModel.sports.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Deportes") }) }
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
                            bottom = 8.dp, start = 16.dp, end = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { it.id }) { sport ->
                            SportCard(sport = sport)
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun SportCard(sport: Sport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sport.name, style = MaterialTheme.typography.titleLarge)
            Text(sport.description, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${sport.playersPerTeam}v${sport.playersPerTeam}", style = MaterialTheme.typography.labelSmall)
                Text("${sport.durationMinutes} min", style = MaterialTheme.typography.labelSmall)
                Text("Anota en ${sport.scoringUnit}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
