package com.example.hayequipoapp.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
