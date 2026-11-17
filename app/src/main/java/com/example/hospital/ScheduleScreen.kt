package com.example.hospital

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private fun isoOfCal(c: Calendar) = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
private fun shortDate(date: String) = "${date.substring(8)}.${date.substring(5, 7)}"

// Статус врача «сейчас»: не работает / принимает / свободен (по реальному времени телефона)
private fun nowStatus(d: Doctor, busyNow: Boolean): Pair<String, Color> {
    val c = Calendar.getInstance(); val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    val works = isoOfCal(c) in workDaySet(d) && m >= toMin(d.workStart) && m < toMin(d.workEnd)
    return when {
        !works -> "Не работает" to Color(0xFF757575)
        busyNow -> "Принимает" to Color(0xFF1A73E8)
        else -> "Свободен" to Color(0xFF1E8E3E)
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(14.dp)) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp))
    }
}

// Вкладка «Расписание»: список врачей, по нажатию карточка врача с неделей
@Composable
fun ScheduleScreen(db: AppDb) {
    val doctors by db.doctors().all().collectAsState(emptyList())
    var sel by rememberSaveable { mutableLongStateOf(0L) }
    val inProg by db.appointments().inProgress(dateStr(Calendar.getInstance())).collectAsState(emptyList())
    val doc = doctors.firstOrNull { it.doctorId == sel }
    if (doc == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Врачи: нажми на врача, чтобы увидеть расписание", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            doctors.forEach { d ->
                val (st, col) = nowStatus(d, d.doctorId in inProg)
                ElevatedCard(Modifier.fillMaxWidth().clickable { sel = d.doctorId }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.fullName, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Text("${d.specialization} · каб. ${d.room ?: "—"}", fontSize = 13.sp)
                        }
                        StatusBadge(st, col)
                    }
                }
            }
        }
    } else DoctorCard(db, doc, doc.doctorId in inProg) { sel = 0L }
}

@Composable
private fun DoctorCard(db: AppDb, d: Doctor, busyNow: Boolean, onBack: () -> Unit) {
    val days = (0..6).map { i -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) } }
    val rows by db.appointments().weekRows(d.doctorId, dateStr(days.first()), dateStr(days.last())).collectAsState(emptyList())
    val busySet = rows.map { it.apptDatetime }.toSet()
    val now = Calendar.getInstance(); val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    // ближайшее свободное время
    var nextFree: String? = null
    loop@ for ((i, c) in days.withIndex()) {
        val iso = isoOfCal(c); if (iso !in workDaySet(d)) continue
        val date = dateStr(c)
        for (t in slotsOf(d)) {
            if (i == 0 && toMin(t) <= nowMin) continue
            if ("$date $t" !in busySet) { nextFree = "${DAY_NAMES[iso - 1]} ${shortDate(date)} в $t"; break@loop }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onBack) { Text("← К списку врачей") }
        Text(d.fullName, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text("${d.specialization} · каб. ${d.room ?: "—"}", fontSize = 15.sp)
        d.phone?.let { Text("Телефон: $it", fontSize = 14.sp) }
        val (st, col) = nowStatus(d, busyNow)
        StatusBadge(st, col)
        Text("График: ${workDaySet(d).sorted().joinToString(", ") { DAY_NAMES[it - 1] }}, ${d.workStart}–${d.workEnd}", fontSize = 14.sp)
        Text(nextFree?.let { "Ближайшее свободное время: $it" } ?: "Свободного времени на неделе нет", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        HorizontalDivider()
        Text("Расписание на неделю", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        days.forEach { c ->
            val date = dateStr(c); val iso = isoOfCal(c); val works = iso in workDaySet(d)
            val dayRows = rows.filter { it.apptDatetime.startsWith(date) }
            val total = if (works) slotsOf(d).size else 0
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row {
                        Text("${DAY_NAMES[iso - 1]} ${shortDate(date)}", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(if (works) "Свободно ${(total - dayRows.size).coerceAtLeast(0)} из $total" else "Выходной", fontSize = 13.sp)
                    }
                    dayRows.forEach {
                        val label = when (it.status) { "запланирован" -> "ожидается"; else -> it.status }
                        Text("${it.apptDatetime.takeLast(5)} · ${it.fullName} · $label", fontSize = 14.sp)
                    }
                    if (works && dayRows.isEmpty()) Text("Записей нет", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
