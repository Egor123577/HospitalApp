package com.example.hospital.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.AppCard
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.hospital.wear.model.QueueState
import com.example.hospital.wear.model.Visit
import com.example.hospital.wear.model.VisitStatus
import com.example.hospital.wear.model.hhmm

private val Green = Color(0xFF1E8E3E)
private val Orange = Color(0xFFB26A00)
private val Blue = Color(0xFF1A73E8)
private val Red = Color(0xFFD93025)

// Главный экран часов врача: карточка текущего пациента и две крупные кнопки.
@Composable
fun VisitScreen(state: QueueState) {
    val v = state.current
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { StatusBadge(v) }
            item { VisitCard(v) }
            item {
                ActionChip(primaryLabel(v), state.focus == 0, v.status != VisitStatus.DONE) { state.focus = 0; state.primaryAction() }
            }
            item { ActionChip("Следующий", state.focus == 1, true) { state.focus = 1; state.next() } }
            item { Text("Пациент ${state.index + 1} из ${state.visits.size}", fontSize = 12.sp, color = MaterialTheme.colors.onSurfaceVariant) }
        }
    }
}

private fun primaryLabel(v: Visit) = when (v.status) {
    VisitStatus.WAITING -> "Пришёл"
    VisitStatus.ARRIVED -> "Завершить"
    VisitStatus.DONE -> "Готово"
}

// Цветная метка статуса над карточкой
@Composable
private fun StatusBadge(v: Visit) {
    val (text, color) = when (v.status) {
        VisitStatus.WAITING -> "Ожидается" to Orange
        VisitStatus.ARRIVED -> "Пришёл в ${v.arrivedMin?.hhmm()}" to Blue
        VisitStatus.DONE -> "Завершён" to Green
    }
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White,
        modifier = Modifier.background(color, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 3.dp)
    )
}

// Карточка пациента по гайдлайнам Wear OS (AppCard)
@Composable
private fun VisitCard(v: Visit) {
    AppCard(
        onClick = {},
        appName = { Text("Каб. ${v.room}") },
        title = { Text(v.name, fontWeight = FontWeight.Medium) },
        time = { Text(v.scheduledMin.hhmm()) },
        content = {
            Text("${v.gender}, ${v.birthYear} г.р.", fontSize = 12.sp)
            Text(v.reason, fontSize = 12.sp)
            v.alert?.let { Text(it, fontSize = 12.sp, color = Red, fontWeight = FontWeight.Medium) }
        }
    )
}

// Крупная кнопка действия; рамка показывает фокус (им управляют жесты, блок 3)
@Composable
private fun ActionChip(label: String, focused: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val ring = if (focused) Modifier.border(3.dp, MaterialTheme.colors.secondary, RoundedCornerShape(24.dp)) else Modifier
    Chip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = ring
    )
}
