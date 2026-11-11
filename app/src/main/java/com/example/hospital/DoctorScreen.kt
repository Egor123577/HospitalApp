package com.example.hospital

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ---------- вспомогательное ----------
private const val DOCTOR_ID = 1L        // Иванова А. С.
private const val LATE_AFTER = 10       // пациент опаздывает через 10 минут после времени записи
private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)
private fun QueueRow.minutes(): Int { val t = apptDatetime.takeLast(5); return t.substring(0, 2).toInt() * 60 + t.substring(3, 5).toInt() }
private fun cycle(p: String) = when (p) { "LIGHT" -> "HEAVY"; "HEAVY" -> "OFF"; else -> "LIGHT" }

@Suppress("DEPRECATION")
private fun vibrator(ctx: Context) = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

// Обычная вибрация по пресету (LIGHT / HEAVY / OFF) + запись в Logcat (тег Haptic)
private fun buzz(ctx: Context, preset: String, tag: String, log: (String) -> Unit) {
    if (preset == "OFF") { log("$tag: вибрация выключена"); return }
    val heavy = preset == "HEAVY"; val ms = if (heavy) 80L else 20L; val amp = if (heavy) 255 else 60
    val v = vibrator(ctx)
    if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, amp)) else @Suppress("DEPRECATION") v.vibrate(ms)
    Log.d("Haptic", "$tag $preset ${ms}ms amp $amp"); log("$tag: $preset, ${ms} мс")
}

// Тройная вибрация: сигнал «пациент опаздывает»
private fun buzzLate(ctx: Context, name: String, log: (String) -> Unit) {
    val pattern = longArrayOf(0, 100, 60, 100, 60, 100)
    val v = vibrator(ctx)
    if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1)) else @Suppress("DEPRECATION") v.vibrate(pattern, -1)
    Log.d("Haptic", "LATE_ALERT $name"); log("СИГНАЛ ОПОЗДАНИЯ: $name")
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(14.dp)) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
    }
}

// ---------- экран врача ----------
@Composable
fun DoctorScreen(db: AppDb) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = db.appointments()
    val rows by dao.queue(DOCTOR_ID).collectAsState(emptyList())
    var index by rememberSaveable { mutableIntStateOf(0) }
    var focus by rememberSaveable { mutableIntStateOf(0) }       // 0 = верхняя кнопка, 1 = нижняя
    var simT by rememberSaveable { mutableIntStateOf(600) }      // время на экране в минутах (10:00)
    var pinchP by rememberSaveable { mutableStateOf("LIGHT") }
    var fistP by rememberSaveable { mutableStateOf("HEAVY") }
    val lines = remember { mutableStateListOf<String>() }
    fun log(s: String) { lines.add(0, s); if (lines.size > 6) lines.removeAt(lines.lastIndex) }

    val cur = rows.getOrNull(index.coerceIn(0, maxOf(rows.size - 1, 0)))
    fun isLate(r: QueueRow) = r.status == "запланирован" && simT > r.minutes() + LATE_AFTER
    val lateCount = rows.count { isLate(it) }

    // --- действия, которые вызывают и кнопки, и (позже) жесты ---
    fun next(tag: String) {
        if (rows.isNotEmpty()) index = (index + 1) % rows.size
        focus = 0; buzz(ctx, pinchP, tag, ::log)
    }
    fun primary() {
        val r = cur ?: return
        scope.launch {
            when (r.status) {
                "запланирован" -> { dao.setArrival(r.appointmentId, "пришёл", fmt(simT)); log("БД: приём ${r.appointmentId}, пришёл в ${fmt(simT)}") }
                "пришёл" -> { dao.setStatus(r.appointmentId, "завершён"); log("БД: приём ${r.appointmentId} завершён") }
                else -> log("Приём уже завершён")
            }
        }
        buzz(ctx, fistP, "КУЛАК (ОК)", ::log)
    }
    fun fist() { if (focus == 0) primary() else next("КУЛАК -> следующий") }
    fun advance(d: Int) {
        val old = simT; simT += d
        rows.filter { it.status == "запланирован" && old <= it.minutes() + LATE_AFTER && simT > it.minutes() + LATE_AFTER }
            .forEach { buzzLate(ctx, it.fullName, ::log) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Врач: Иванова А. С.", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Время: ${fmt(simT)}", fontSize = 16.sp)
            if (lateCount > 0) Text("Опаздывают: $lateCount", fontSize = 16.sp, color = Color(0xFFD93025), fontWeight = FontWeight.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ advance(5) }) { Text("+5 мин") }
            OutlinedButton({ advance(15) }) { Text("+15 мин") }
            OutlinedButton({ scope.launch { dao.resetQueue(DOCTOR_ID) }; simT = 600; index = 0; focus = 0; lines.clear() }) { Text("Сброс") }
        }

        if (cur == null) { Text("Нет приёмов у этого врача") }
        else {
            val late = isLate(cur)
            when {
                cur.status == "пришёл" -> Badge("Пришёл в ${cur.arrivedAt}", Color(0xFF1A73E8))
                cur.status == "завершён" -> Badge("Завершён", Color(0xFF1E8E3E))
                late -> Badge("Опаздывает +${simT - cur.minutes()} мин", Color(0xFFD93025))
                else -> Badge("Ожидается", Color(0xFFB26A00))
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(cur.fullName, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Text("${cur.gender}, ${cur.birthDate.take(4)} г.р. · каб. ${cur.room ?: "—"}", fontSize = 15.sp)
                    Text(cur.reason ?: "", fontSize = 15.sp)
                    Text("Запись на ${fmt(cur.minutes())}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val primaryLabel = when (cur.status) { "запланирован" -> "Пришёл"; "пришёл" -> "Завершить"; else -> "Готово" }
            Button({ focus = 0; primary() }, Modifier.fillMaxWidth().height(60.dp), enabled = cur.status != "завершён",
                border = if (focus == 0) BorderStroke(4.dp, MaterialTheme.colorScheme.tertiary) else null) { Text(primaryLabel, fontSize = 20.sp) }
            FilledTonalButton({ focus = 1; next("Следующий пациент") }, Modifier.fillMaxWidth().height(60.dp),
                border = if (focus == 1) BorderStroke(4.dp, MaterialTheme.colorScheme.tertiary) else null) {
                Text(if (late) "Позвать следующего" else "Следующий", fontSize = 20.sp)
            }
            Text("Пациент ${rows.indexOf(cur) + 1} из ${rows.size}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HorizontalDivider()
        Text("Жесты (имитация кнопками)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton({ next("ДВОЙНОЙ ЩИПОК") }) { Text("Двойной щипок", fontSize = 12.sp) }
            OutlinedButton({ focus = (focus + 1) % 2; buzz(ctx, pinchP, "ЩИПОК -> фокус $focus", ::log) }) { Text("Щипок", fontSize = 12.sp) }
            OutlinedButton({ fist() }) { Text("Кулак: ОК", fontSize = 12.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ pinchP = cycle(pinchP) }) { Text("Щипки: $pinchP") }
            TextButton({ fistP = cycle(fistP) }) { Text("Кулак: $fistP") }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text(if (lines.isEmpty()) "Журнал событий" else lines.joinToString("\n"), fontSize = 12.sp, modifier = Modifier.padding(10.dp))
        }
    }
}
