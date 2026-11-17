package com.example.hospital

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Free = Color(0xFFE3F2E5)        // свободно (врач работает, приёмов нет)
private val Light = Color(0xFFFFE9C7)       // мало приёмов
private val Heavy = Color(0xFFD93025)       // много приёмов
private val OffDuty = Color(0x33808080)     // врач не работает

/** Тепловая карта загрузки: дни недели × часы, цвет = число приёмов за всё время. */
@Composable
fun HeatmapScreen(db: AppDb) {
    val doctors by db.doctors().all().collectAsState(emptyList())
    var sel by rememberSaveable { mutableLongStateOf(0L) }           // 0 = все врачи
    val rows by db.appointments().heat(sel).collectAsState(emptyList())
    val doc = doctors.firstOrNull { it.doctorId == sel }

    // (день недели 1..7, час) -> число приёмов. В SQLite воскресенье = 0, переводим в 7.
    val cells = rows.associate { Pair(if (it.dow == 0) 7 else it.dow, it.hour) to it.cnt }
    val max = (cells.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val top = cells.maxByOrNull { it.value }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Загрузка врачей", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(sel == 0L, { sel = 0L }, label = { Text("Все врачи") })
            doctors.forEach { d -> FilterChip(sel == d.doctorId, { sel = d.doctorId }, label = { Text(d.fullName.substringBefore(' ')) }) }
        }
        Text("Всего приёмов: ${cells.values.sum()}", fontSize = 14.sp)
        top?.let { Text("Самое загруженное время: ${DAY_NAMES[it.key.first - 1]} ${"%02d".format(it.key.second)}:00 (${it.value})", fontSize = 14.sp) }

        // шапка: дни недели
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(34.dp))
            DAY_NAMES.forEach { Text(it, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center) }
        }
        // строки: часы
        (8..20).forEach { h ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("%02d".format(h), Modifier.width(34.dp), fontSize = 12.sp)
                (1..7).forEach { iso ->
                    val n = cells[Pair(iso, h)] ?: 0
                    val works = doc == null || (iso in workDaySet(doc) && h * 60 >= toMin(doc.workStart) && h * 60 < toMin(doc.workEnd))
                    val t = n.toFloat() / max
                    val color = when { !works -> OffDuty; n == 0 -> Free; else -> lerp(Light, Heavy, t) }
                    Box(Modifier.weight(1f).height(28.dp).padding(1.dp).background(color, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        if (n > 0) Text(n.toString(), fontSize = 11.sp, color = if (t > 0.6f) Color.White else Color.Black)
                    }
                }
            }
        }
        // легенда
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(14.dp).background(Free, RoundedCornerShape(3.dp))); Text("свободно", fontSize = 12.sp)
            Box(Modifier.size(14.dp).background(Light, RoundedCornerShape(3.dp))); Text("мало", fontSize = 12.sp)
            Box(Modifier.size(14.dp).background(Heavy, RoundedCornerShape(3.dp))); Text("много", fontSize = 12.sp)
            if (doc != null) { Box(Modifier.size(14.dp).background(OffDuty, RoundedCornerShape(3.dp))); Text("не работает", fontSize = 12.sp) }
        }
    }
}
