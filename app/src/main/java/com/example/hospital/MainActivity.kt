package com.example.hospital

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDb.get(this)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App(db) } } }
    }
}

// ---------- валидация ----------
fun String.req(name: String) = trim().also { require(it.isNotEmpty()) { "Поле «$name» обязательно" } }
fun String.opt() = trim().ifEmpty { null }
fun String.num(name: String) = trim().toLongOrNull() ?: throw IllegalArgumentException("«$name» должно быть числом")
fun String.oneOf(name: String, vararg o: String) = trim().also { require(it in o) { "«$name»: ${o.joinToString(" / ")}" } }

@Composable
fun App(db: AppDb) {
    val tabs = listOf("Пациенты", "Врачи", "Отделения", "Приёмы", "Госпитализации")
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.statusBarsPadding()) {
        ScrollableTabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> { val d = db.patients(); val l by d.all().collectAsState(emptyList())
                CrudScreen(l, listOf("ФИО", "Дата рождения (ГГГГ-ММ-ДД)", "Пол (М/Ж)", "Адрес", "Телефон", "Полис ОМС"),
                    { it.fullName }, { "${it.birthDate} • ${it.gender} • полис ${it.policyNumber ?: "—"}" },
                    { listOf(it.fullName, it.birthDate, it.gender, it.address ?: "", it.phone ?: "", it.policyNumber ?: "") },
                    { o, v -> d.save(o, Patient(o?.patientId ?: 0, v[0].req("ФИО"), v[1].req("Дата рождения"), v[2].oneOf("Пол", "М", "Ж"), v[3].opt(), v[4].opt(), v[5].opt())) },
                    { d.delete(it) }) }
            1 -> { val d = db.doctors(); val l by d.all().collectAsState(emptyList())
                CrudScreen(l, listOf("ФИО", "Специализация", "ID отделения", "Телефон", "Email", "Дата найма"),
                    { it.fullName }, { "${it.specialization} • отделение №${it.departmentId}" },
                    { listOf(it.fullName, it.specialization, it.departmentId.toString(), it.phone ?: "", it.email ?: "", it.hireDate ?: "") },
                    { o, v -> d.save(o, Doctor(o?.doctorId ?: 0, v[2].num("ID отделения"), v[0].req("ФИО"), v[1].req("Специализация"), v[3].opt(), v[4].opt(), v[5].opt())) },
                    { d.delete(it) }) }
            2 -> { val d = db.departments(); val l by d.all().collectAsState(emptyList())
                CrudScreen(l, listOf("Название", "Этаж", "Телефон"),
                    { it.name }, { "ID ${it.departmentId} • этаж ${it.floor ?: "—"} • ${it.phone ?: ""}" },
                    { listOf(it.name, it.floor?.toString() ?: "", it.phone ?: "") },
                    { o, v -> d.save(o, Department(o?.departmentId ?: 0, v[0].req("Название"), v[1].opt()?.num("Этаж")?.toInt(), v[2].opt())) },
                    { d.delete(it) }) }
            3 -> { val d = db.appointments(); val l by d.all().collectAsState(emptyList())
                CrudScreen(l, listOf("ID пациента", "ID врача", "Дата и время (ГГГГ-ММ-ДД ЧЧ:ММ)", "Статус (запланирован/завершён/отменён/не явился)", "Причина"),
                    { "${it.apptDatetime} • ${it.status}" }, { "пациент №${it.patientId}, врач №${it.doctorId}. ${it.reason ?: ""}" },
                    { listOf(it.patientId.toString(), it.doctorId.toString(), it.apptDatetime, it.status, it.reason ?: "") },
                    { o, v -> d.save(o, Appointment(o?.appointmentId ?: 0, v[0].num("ID пациента"), v[1].num("ID врача"), v[2].req("Дата"),
                        v[3].oneOf("Статус", "запланирован", "завершён", "отменён", "не явился"), v[4].opt())) },
                    { d.delete(it) }) }
            else -> { val d = db.hospitalizations(); val l by d.all().collectAsState(emptyList())
                CrudScreen(l, listOf("ID пациента", "ID койки", "ID врача", "ID диагноза", "Дата поступления", "Дата выписки", "Статус (проходит лечение/выписан/переведён)"),
                    { "Пациент №${it.patientId} • ${it.status}" }, { "койка №${it.bedId}, врач №${it.doctorId}, с ${it.admissionDate}" },
                    { listOf(it.patientId.toString(), it.bedId.toString(), it.doctorId.toString(), it.diagnosisId?.toString() ?: "", it.admissionDate, it.dischargeDate ?: "", it.status) },
                    { o, v ->
                        val h = Hospitalization(o?.hospitalizationId ?: 0, v[0].num("ID пациента"), v[1].num("ID койки"), v[2].num("ID врача"),
                            v[3].opt()?.num("ID диагноза"), v[4].req("Дата поступления"), v[5].opt(),
                            v[6].oneOf("Статус", "проходит лечение", "выписан", "переведён"))
                        require(h.dischargeDate == null || h.dischargeDate >= h.admissionDate) { "Выписка не может быть раньше поступления" }
                        if (o == null) d.admit(h) else d.updateAndSync(h)   // транзакции
                    },
                    { d.delete(it) }) }
        }
    }
}

/** Универсальный экран: список + добавить/изменить/удалить. */
@Composable
fun <T> CrudScreen(
    items: List<T>, fields: List<String>, title: (T) -> String, sub: (T) -> String, values: (T) -> List<String>,
    save: suspend (T?, List<String>) -> Unit, del: suspend (T) -> Unit
) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<T?>(null) }
    val vals = remember { mutableStateListOf<String>() }
    var msg by remember { mutableStateOf<String?>(null) }

    fun open(item: T?) { editing = item; vals.clear(); vals.addAll(item?.let(values) ?: fields.map { "" }); form = true }
    fun fail(e: Exception) { msg = e.message ?: "Ошибка" }

    Scaffold(floatingActionButton = { FloatingActionButton({ open(null) }) { Text("+") } }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { it ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(title(it), style = MaterialTheme.typography.titleMedium); Text(sub(it), style = MaterialTheme.typography.bodySmall) }
                        TextButton({ open(it) }) { Text("Изм.") }
                        TextButton({ scope.launch { try { del(it) } catch (e: Exception) { fail(e) } } }) { Text("Удал.") }
                    }
                }
            }
        }
    }
    if (form) AlertDialog(
        onDismissRequest = { form = false },
        title = { Text(if (editing == null) "Добавить" else "Изменить") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            fields.forEachIndexed { i, f -> OutlinedTextField(vals[i], { vals[i] = it }, label = { Text(f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) }
        } },
        confirmButton = { TextButton({ scope.launch { try { save(editing, vals.toList()); form = false } catch (e: Exception) { fail(e) } } }) { Text("Сохранить") } },
        dismissButton = { TextButton({ form = false }) { Text("Отмена") } }
    )
    msg?.let { AlertDialog({ msg = null }, confirmButton = { TextButton({ msg = null }) { Text("OK") } }, title = { Text("Ошибка") }, text = { Text(it) }) }
}
