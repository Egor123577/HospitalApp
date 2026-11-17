package com.example.hospital

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

// Выпадающий список выбора (пациент, врач)
@Composable
private fun <T> Picker(label: String, items: List<T>, selected: T?, text: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth()) { Text("$label: ${selected?.let(text) ?: "—"}") }
        DropdownMenu(open, { open = false }) {
            items.forEach { DropdownMenuItem(text = { Text(text(it)) }, onClick = { onPick(it); open = false }) }
        }
    }
}

// Запись на приём: сетка времени врача, занятые слоты недоступны
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookingScreen(db: AppDb) {
    val scope = rememberCoroutineScope()
    val patients by db.patients().all().collectAsState(emptyList())
    val doctors by db.doctors().all().collectAsState(emptyList())
    var patientId by rememberSaveable { mutableLongStateOf(0L) }
    var doctorId by rememberSaveable { mutableLongStateOf(0L) }
    var date by rememberSaveable { mutableStateOf(today()) }
    var slot by rememberSaveable { mutableStateOf<String?>(null) }
    var reason by rememberSaveable { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }

    val pat = patients.firstOrNull { it.patientId == patientId } ?: patients.firstOrNull()
    val doc = doctors.firstOrNull { it.doctorId == doctorId } ?: doctors.firstOrNull()
    val dateOk = Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)
    val busy by db.appointments().busySlots(doc?.doctorId ?: 0L, if (dateOk) date else "").collectAsState(emptyList())
    val busySet = busy.toSet()
    val times = if (doc != null && dateOk && isoOf(date) in workDaySet(doc)) slotsOf(doc) else emptyList()   // по графику врача

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Запись на приём", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Picker("Пациент", patients, pat, { it.fullName }) { patientId = it.patientId }
        Picker("Врач", doctors, doc, { "${it.fullName} (${it.specialization})" }) { doctorId = it.doctorId; slot = null }
        OutlinedTextField(date, { date = it; slot = null }, label = { Text("Дата (ГГГГ-ММ-ДД)") }, singleLine = true,
            isError = !dateOk, modifier = Modifier.fillMaxWidth())
        Text("Время (занятое недоступно)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (dateOk && doc != null && times.isEmpty()) Text("Врач в этот день не работает", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            times.forEach { t ->
                val taken = "$date $t" in busySet
                FilterChip(selected = slot == t, onClick = { slot = t }, enabled = dateOk && !taken,
                    label = { Text(if (taken) "$t занято" else t) })
            }
        }
        OutlinedTextField(reason, { reason = it }, label = { Text("Причина визита") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                scope.launch {
                    try {
                        db.book(Appointment(patientId = pat!!.patientId, doctorId = doc!!.doctorId, apptDatetime = "$date $slot",
                            status = "запланирован", reason = reason.ifBlank { null }))
                        msg = "Записано: ${pat.fullName}, $date $slot"; slot = null; reason = ""
                    } catch (e: Exception) { msg = e.message ?: "Ошибка записи" }
                }
            },
            enabled = slot != null && pat != null && doc != null && dateOk,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Записать", fontSize = 18.sp) }
        msg?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary) }
        HorizontalDivider()
        ReminderSettings(db)
    }
}
