package com.example.hayequipoapp.ui.groups

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
import com.example.hayequipoapp.domain.repository.FriendGroupRepository
import com.example.hayequipoapp.domain.repository.PlayerRepository
import com.example.hayequipoapp.ui.common.EmptyScreen
import com.example.hayequipoapp.ui.common.ErrorScreen
import com.example.hayequipoapp.ui.common.LoadingScreen
import com.example.hayequipoapp.ui.common.SectionHeader
import com.example.hayequipoapp.ui.common.UiState
import com.google.firebase.auth.FirebaseAuth
import com.example.hayequipoapp.data.model.FriendGroup
import com.example.hayequipoapp.data.model.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── List ViewModel ───────────────────────────────────────
@HiltViewModel
class FriendGroupListViewModel @Inject constructor(
    private val groupRepository: FriendGroupRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _groups = MutableStateFlow<UiState<List<FriendGroup>>>(UiState.Loading)
    val groups = _groups.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog = _showCreateDialog.asStateFlow()

    init { load() }

    fun load() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            groupRepository.getFriendGroupsByPlayer(uid).collect {
                _groups.value = UiState.Success(it)
            }
        }
    }

    fun openCreateDialog()  { _showCreateDialog.value = true }
    fun closeCreateDialog() { _showCreateDialog.value = false }

    fun createGroup(name: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val group = FriendGroup(name = name, createdBy = uid, memberIds = listOf(uid))
            groupRepository.createFriendGroup(group)
            closeCreateDialog()
        }
    }
}

// ─── Detail ViewModel ─────────────────────────────────────
@HiltViewModel
class FriendGroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: FriendGroupRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _group   = MutableStateFlow<UiState<FriendGroup>>(UiState.Loading)
    val group = _group.asStateFlow()

    private val _members = MutableStateFlow<List<Player>>(emptyList())
    val members = _members.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val g = groupRepository.getFriendGroupById(groupId)
                if (g != null) {
                    _group.value = UiState.Success(g)
                    // Cargar perfiles de los miembros
                    val players = g.memberIds.mapNotNull { playerRepository.getPlayerById(it) }
                    _members.value = players
                } else {
                    _group.value = UiState.Error("Grupo no encontrado")
                }
            } catch (e: Exception) {
                _group.value = UiState.Error(e.message ?: "Error")
            }
        }
    }

    fun removeMember(playerId: String) {
        val current = (_group.value as? UiState.Success)?.data ?: return
        val updated = current.copy(memberIds = current.memberIds.filter { it != playerId })
        viewModelScope.launch {
            groupRepository.updateFriendGroup(updated)
            _group.value = UiState.Success(updated)
            _members.value = _members.value.filter { it.id != playerId }
        }
    }
}

// ─── FriendGroupListScreen ────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendGroupListScreen(
    onGroupClick: (String) -> Unit,
    viewModel: FriendGroupListViewModel = hiltViewModel()
) {
    val state        by viewModel.groups.collectAsState()
    val showDialog   by viewModel.showCreateDialog.collectAsState()
    var newGroupName by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mis grupos") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openCreateDialog) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo grupo")
            }
        }
    ) { padding ->
        when (state) {
            is UiState.Loading -> LoadingScreen()
            is UiState.Error   -> ErrorScreen((state as UiState.Error).message)
            is UiState.Success -> {
                val list = (state as UiState.Success).data
                if (list.isEmpty()) {
                    EmptyScreen("Todavía no tenés grupos.\n¡Creá uno para invitar rápido!")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 8.dp,
                            start = 16.dp, end = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { it.id }) { group ->
                            GroupCard(group = group, onClick = { onGroupClick(group.id) })
                        }
                    }
                }
            }
            else -> {}
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = viewModel::closeCreateDialog,
                title   = { Text("Nuevo grupo") },
                text    = {
                    OutlinedTextField(
                        value         = newGroupName,
                        onValueChange = { newGroupName = it },
                        label         = { Text("Nombre del grupo") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.createGroup(newGroupName)
                        newGroupName = ""
                    }) { Text("Crear") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::closeCreateDialog) { Text("Cancelar") }
                }
            )
        }
    }
}

// ─── FriendGroupDetailScreen ──────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendGroupDetailScreen(
    groupId: String,
    onBack:  () -> Unit,
    viewModel: FriendGroupDetailViewModel = hiltViewModel()
) {
    val groupState by viewModel.group.collectAsState()
    val members    by viewModel.members.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val name = (groupState as? UiState.Success)?.data?.name ?: "Grupo"
                    Text(name)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when (groupState) {
            is UiState.Loading -> LoadingScreen()
            is UiState.Error   -> ErrorScreen((groupState as UiState.Error).message)
            is UiState.Success -> {
                val group = (groupState as UiState.Success).data
                Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                    Text("${members.size} miembros", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    SectionHeader("Miembros")
                    members.forEach { player ->
                        ListItem(
                            headlineContent  = { Text(player.name) },
                            supportingContent = { Text(player.position.ifBlank { "Sin posición" }) },
                            trailingContent  = {
                                if (group.createdBy != player.id) {
                                    TextButton(onClick = { viewModel.removeMember(player.id) }) {
                                        Text("Quitar")
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
            else -> {}
        }
    }
}

// ─── GroupCard ────────────────────────────────────────────
@Composable
private fun GroupCard(group: FriendGroup, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleLarge)
            Text("${group.memberIds.size} miembros", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
