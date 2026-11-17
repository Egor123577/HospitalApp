package com.example.hospital

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Общие функции графика работы врача (дни недели: 1 = пн ... 7 = вс)
val DAY_NAMES = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
const val SLOT_MIN = 20                                   // длина приёма в минутах

fun toMin(t: String): Int = runCatching { val p = t.trim().split(":"); p[0].toInt() * 60 + p[1].toInt() }.getOrDefault(0)
fun hhmmOf(m: Int) = "%02d:%02d".format(m / 60, m % 60)
fun workDaySet(d: Doctor): Set<Int> = d.workDays.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
fun slotsOf(d: Doctor): List<String> { val s = toMin(d.workStart); val e = toMin(d.workEnd); return (s until e step SLOT_MIN).filter { it + SLOT_MIN <= e }.map { hhmmOf(it) } }
fun dateStr(c: Calendar): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)

// День недели по дате ГГГГ-ММ-ДД (null, если дата неверная)
fun isoOf(date: String): Int? {
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val dt = runCatching { f.parse(date) }.getOrNull() ?: return null
    val c = Calendar.getInstance().apply { time = dt }
    return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
}

// Работает ли врач в указанные дату и время ("ГГГГ-ММ-ДД ЧЧ:ММ")
fun isWorkingAt(d: Doctor, datetime: String): Boolean {
    val iso = isoOf(datetime.take(10)) ?: return false
    val t = toMin(datetime.takeLast(5))
    return iso in workDaySet(d) && t >= toMin(d.workStart) && t < toMin(d.workEnd)
}
