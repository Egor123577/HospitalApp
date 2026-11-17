package com.example.hospital

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

// Загрузка пациентов из .xlsx. Читаем ПЕРВЫЙ лист; в шапке ищем столбцы по названиям:
// ФИО, Дата рождения, Пол, Адрес, Телефон, Номер полиса (порядок столбцов любой).
object ExcelImporter {
    suspend fun importPatients(ctx: Context, db: AppDb, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val rows = readFirstSheet(ctx, uri)
            val h = rows.indexOfFirst { r -> r.values.any { it.lowercase().let { s -> s.contains("фио") || s.contains("ф.и.о") } } }
            if (h < 0) return@withContext "Не найден столбец «ФИО» в шапке. Проверь, что пациенты на первом листе."
            val head = rows[h]
            fun col(vararg keys: String) = head.entries.firstOrNull { e -> keys.any { e.value.lowercase().contains(it) } }?.key
            val cName = col("фио", "ф.и.о")!!
            val cBirth = col("рожд"); val cGender = col("пол"); val cAddr = col("адрес"); val cPhone = col("телефон"); val cPolicy = col("полис")
            if (cBirth == null || cGender == null) return@withContext "Нужны столбцы «Дата рождения» и «Пол»."
            var added = 0; val skipped = mutableListOf<String>()
            for ((i, r) in rows.drop(h + 1).withIndex()) {
                val line = h + i + 2
                val name = r[cName] ?: continue
                val bd = birth(clean(r[cBirth] ?: "")) ?: run { skipped += "строка $line: дата рождения не распознана"; null } ?: continue
                val g = gender(r[cGender] ?: "") ?: run { skipped += "строка $line: пол должен быть М или Ж"; null } ?: continue
                val policy = cPolicy?.let { r[it] }?.let { clean(it) }
                if (policy != null && db.patients().countPolicy(policy) > 0) { skipped += "строка $line: полис $policy уже есть"; continue }
                try {
                    db.patients().insert(Patient(fullName = name, birthDate = bd, gender = g,
                        address = cAddr?.let { r[it] }, phone = cPhone?.let { r[it] }?.let { clean(it) }, policyNumber = policy))
                    added++
                } catch (e: Exception) { skipped += "строка $line: ${e.message}" }
            }
            "Добавлено: $added. Пропущено: ${skipped.size}." + if (skipped.isEmpty()) "" else "\n" + skipped.take(6).joinToString("\n")
        } catch (e: Exception) { "Ошибка чтения файла: ${e.message}" }
    }

    // Числа вида 1.23E15 и 5.0 превращаем в обычные строки
    private fun clean(v: String): String {
        val s = v.trim()
        if (Regex("^-?\\d+(\\.\\d+)?[Ee][+-]?\\d+$").matches(s)) return BigDecimal(s).toPlainString()
        if (Regex("^\\d+\\.0$").matches(s)) return s.dropLast(2)
        return s
    }

    private fun gender(v: String): String? = when (v.trim().lowercase()) {
        "м", "муж", "мужской", "m" -> "М"
        "ж", "жен", "женский", "f" -> "Ж"
        else -> null
    }

    // Дата: ГГГГ-ММ-ДД, ДД.ММ.ГГГГ или число-дата Excel
    private fun birth(v: String): String? {
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(v)) return v
        Regex("^(\\d{2})\\.(\\d{2})\\.(\\d{4})$").matchEntire(v)?.let { return "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}" }
        v.toDoubleOrNull()?.let { d ->
            if (d in 10000.0..80000.0) {
                val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                return f.format(Date(((d - 25569) * 86400000L).toLong()))
            }
        }
        return null
    }

    // ---------- чтение .xlsx (zip + XML) ----------
    private fun readFirstSheet(ctx: Context, uri: Uri): List<Map<Int, String>> {
        var shared = listOf<String>()
        val sheets = sortedMapOf<Int, ByteArray>()
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name == "xl/sharedStrings.xml") shared = parseShared(z.readBytes())
                    else Regex("xl/worksheets/sheet(\\d+)\\.xml").matchEntire(e.name)?.let { sheets[it.groupValues[1].toInt()] = z.readBytes() }
                    e = z.nextEntry
                }
            }
        }
        val first = sheets.values.firstOrNull() ?: return emptyList()
        return parseSheet(first, shared)
    }

    private fun parseShared(bytes: ByteArray): List<String> {
        val p = Xml.newPullParser(); p.setInput(ByteArrayInputStream(bytes), "UTF-8")
        val out = mutableListOf<String>(); var sb: StringBuilder? = null; var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) { "si" -> sb = StringBuilder(); "t" -> inT = true }
                XmlPullParser.TEXT -> if (inT) sb?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) { "t" -> inT = false; "si" -> { out.add(sb?.toString() ?: ""); sb = null } }
            }
            ev = p.next()
        }
        return out
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) if (ch.isLetter()) n = n * 26 + (ch.uppercaseChar() - 'A' + 1) else break
        return n - 1
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<Map<Int, String>> {
        val p = Xml.newPullParser(); p.setInput(ByteArrayInputStream(bytes), "UTF-8")
        val rows = mutableListOf<Map<Int, String>>()
        var cur: MutableMap<Int, String>? = null
        var col = 0; var type = ""; var capture = false; val text = StringBuilder()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> cur = mutableMapOf()
                    "c" -> { col = colIndex(p.getAttributeValue(null, "r") ?: "A1"); type = p.getAttributeValue(null, "t") ?: ""; text.clear() }
                    "v", "t" -> capture = true
                }
                XmlPullParser.TEXT -> if (capture) text.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> capture = false
                    "c" -> {
                        val raw = text.toString()
                        val value = if (type == "s") shared.getOrElse(raw.trim().toIntOrNull() ?: -1) { "" } else raw
                        if (value.isNotBlank()) cur?.put(col, value.trim())
                    }
                    "row" -> { cur?.let { rows.add(it) }; cur = null }
                }
            }
            ev = p.next()
        }
        return rows
    }
}
