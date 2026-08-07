package com.example.hayequipoapp.ui.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
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
import com.example.hayequipoapp.data.session.CurrentPlayerResolver
import com.example.hayequipoapp.ui.common.EmptyScreen
import com.example.hayequipoapp.ui.common.ErrorScreen
import com.example.hayequipoapp.ui.common.LoadingScreen
import com.example.hayequipoapp.ui.common.SectionHeader
import com.example.hayequipoapp.ui.common.UiState
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
    private val resolver: CurrentPlayerResolver
) : ViewModel() {

    private val _groups = MutableStateFlow<UiState<List<FriendGroup>>>(UiState.Loading)
    val groups = _groups.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog = _showCreateDialog.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val myId = resolver.id() ?: return@launch
            groupRepository.getFriendGroupsByPlayer(myId).collect {
                _groups.value = UiState.Success(it)
            }
        }
    }

    fun openCreateDialog()  { _showCreateDialog.value = true }
    fun closeCreateDialog() { _showCreateDialog.value = false }

    fun createGroup(name: String) {
        viewModelScope.launch {
            val myId = resolver.id() ?: return@launch
            val group = FriendGroup(name = name, createdBy = myId, memberIds = listOf(myId))
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
    private val playerRepository: PlayerRepository,
    private val resolver: CurrentPlayerResolver
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _group   = MutableStateFlow<UiState<FriendGroup>>(UiState.Loading)
    val group = _group.asStateFlow()

    private val _members = MutableStateFlow<List<Player>>(emptyList())
    val members = _members.asStateFlow()

    private val _availablePlayers = MutableStateFlow<UiState<List<Player>>>(UiState.Loading)
    val availablePlayers = _availablePlayers.asStateFlow()

    private val _canManage = MutableStateFlow(false)
    val canManage = _canManage.asStateFlow()

    private val _myId = MutableStateFlow<String?>(null)
    val myId = _myId.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted = _deleted.asStateFlow()

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError = _deleteError.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                _myId.value = resolver.id()
                val g = groupRepository.getFriendGroupById(groupId)
                if (g != null) {
                    _group.value = UiState.Success(g)
                    _canManage.value = g.createdBy == _myId.value
                    // Cargar perfiles de los miembros
                    val players = g.memberIds.mapNotNull { playerRepository.getPlayerById(it) }
                    _members.value = players
                } else {
                    _group.value = UiState.Error("Grupo no encontrado")
                }
            } catch (e: Exception) {
                _group.value = UiState.Error(e.message ?: "Error")
            }
            loadAvailablePlayers()
        }
    }

    fun loadAvailablePlayers() {
        viewModelScope.launch {
            try {
                val memberIds = (_group.value as? UiState.Success)?.data?.memberIds?.toSet() ?: emptySet()
                playerRepository.getPlayers().collect { list ->
                    _availablePlayers.value = UiState.Success(
                        list.filter { it.id !in memberIds }
                    )
                }
            } catch (e: Exception) {
                _availablePlayers.value = UiState.Error(e.message ?: "Error cargando jugadores")
            }
        }
    }

    fun addMembers(playerIds: List<String>) {
        if (playerIds.isEmpty()) return
        val current = (_group.value as? UiState.Success)?.data ?: return
        val updated = current.copy(memberIds = (current.memberIds + playerIds).distinct())
        viewModelScope.launch {
            groupRepository.updateFriendGroup(updated)
            _group.value = UiState.Success(updated)
            load()
        }
    }

    fun renameGroup(newName: String) {
        if (newName.isBlank()) return
        val current = (_group.value as? UiState.Success)?.data ?: return
        val updated = current.copy(name = newName.trim())
        viewModelScope.launch {
            groupRepository.updateFriendGroup(updated)
            _group.value = UiState.Success(updated)
            load()
        }
    }

    fun deleteGroup() {
        viewModelScope.launch {
            _deleteError.value = null
            val result = groupRepository.deleteFriendGroup(groupId)
            if (result.isSuccess) {
                _deleted.value = true
            } else {
                _deleteError.value = "No se pudo eliminar el grupo"
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
            load()
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
    onGroupDeleted: () -> Unit = {},
    viewModel: FriendGroupDetailViewModel = hiltViewModel()
) {
    val groupState by viewModel.group.collectAsState()
    val members    by viewModel.members.collectAsState()
    val canManage  by viewModel.canManage.collectAsState()
    val myId       by viewModel.myId.collectAsState()
    val availablePlayers by viewModel.availablePlayers.collectAsState()
    val deleted    by viewModel.deleted.collectAsState()
    val deleteError by viewModel.deleteError.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(deleted) {
        if (deleted) onGroupDeleted()
    }

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
                },
                actions = {
                    if (canManage) {
                        IconButton(onClick = {
                            newName = (groupState as? UiState.Success)?.data?.name ?: ""
                            showRenameDialog = true
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar nombre")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (groupState) {
            is UiState.Loading -> LoadingScreen()
            is UiState.Error   -> ErrorScreen((groupState as UiState.Error).message)
            is UiState.Success -> {
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
                                val isSelf = myId == player.id
                                when {
                                    canManage && !isSelf -> {
                                        TextButton(onClick = { viewModel.removeMember(player.id) }) {
                                            Text("Quitar")
                                        }
                                    }
                                    !canManage && isSelf -> {
                                        TextButton(onClick = { viewModel.removeMember(player.id) }) {
                                            Text("Salirse")
                                        }
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }

                    if (canManage) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Agregar jugadores")
                        }
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
                            Text("Eliminar grupo")
                        }
                        if (deleteError != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(deleteError!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            else -> {}
        }
    }

    if (showAddDialog) {
        PlayerMultiSelectDialog(
            title = "Agregar jugadores",
            players = (availablePlayers as? UiState.Success)?.data
                ?: if (availablePlayers is UiState.Loading) null else emptyList(),
            confirmButtonText = "Agregar",
            onConfirm = {
                viewModel.addMembers(it)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Editar nombre") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre del grupo") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameGroup(newName)
                    showRenameDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar grupo") },
            text = { Text("¿Eliminar este grupo definitivamente?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup()
                    showDeleteDialog = false
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
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
