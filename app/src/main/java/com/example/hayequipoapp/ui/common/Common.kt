package com.example.hayequipoapp.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hayequipoapp.data.model.Player
import com.example.hayequipoapp.ui.theme.GreenField
import com.example.hayequipoapp.ui.theme.YellowCard

// ─── UI State wrapper ─────────────────────────────────────
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
    object Idle: UiState<Nothing>()
}

// ─── LoadingScreen ────────────────────────────────────────
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = GreenField)
    }
}

// ─── ErrorScreen ──────────────────────────────────────────
@Composable
fun ErrorScreen(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }
}

// ─── EmptyScreen ──────────────────────────────────────────
@Composable
fun EmptyScreen(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─── StarRating ───────────────────────────────────────────
@Composable
fun StarRating(
    value: Int,
    maxValue: Int = 5,
    onStarClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(maxValue) { index ->
            val filled = index < value
            if (onStarClick != null) {
                IconButton(onClick = { onStarClick(index + 1) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        tint = if (filled) YellowCard else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    tint = if (filled) YellowCard else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─── StatusChip ───────────────────────────────────────────
@Composable
fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        "scheduled"  -> "Programado"    to MaterialTheme.colorScheme.primary
        "confirmed"  -> "Confirmado"    to MaterialTheme.colorScheme.primaryContainer
        "in_progress"-> "En juego"      to MaterialTheme.colorScheme.secondary
        "finished"   -> "Finalizado"    to MaterialTheme.colorScheme.onSurfaceVariant
        "cancelled"  -> "Cancelado"     to MaterialTheme.colorScheme.error
        "pending"    -> "Pendiente"     to MaterialTheme.colorScheme.secondary
        "accepted"   -> "Confirmado"    to MaterialTheme.colorScheme.primary
        "rejected"   -> "Rechazado"     to MaterialTheme.colorScheme.error
        "attended"   -> "Asistió"       to MaterialTheme.colorScheme.primary
        "no_show"    -> "No apareció"   to MaterialTheme.colorScheme.error
        else         -> status          to MaterialTheme.colorScheme.outline
    }
    SuggestionChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.15f),
            labelColor = color
        )
    )
}

// ─── SectionHeader ────────────────────────────────────────
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

// ─── DropdownField (selector estable, no experimental) ────
@Composable
fun DropdownField(
    value: String,
    label: String,
    options: List<String>,
    onOptionSelected: (Int) -> Unit,
    emptyMessage: String = "No hay opciones",
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = enabled) { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(text = { Text(emptyMessage) }, onClick = { expanded = false })
            } else {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(index)
                            expanded = false
                        }
                    )
}
}

// ─── PlayerMultiSelectDialog (selección múltiple de jugadores) ──
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
        }
    }
}
