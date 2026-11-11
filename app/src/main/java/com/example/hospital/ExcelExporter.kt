package com.example.hospital

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Выгрузка ВСЕХ 13 таблиц БД в один файл .xlsx (лист = таблица, заголовки по-русски).
// Файл .xlsx это zip с XML внутри, поэтому собираем его вручную, без сторонних библиотек.
object ExcelExporter {
    private const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    // таблица в БД, название листа, столбцы (имя в БД -> заголовок)
    class T(val table: String, val sheet: String, val cols: List<Pair<String, String>>)

    private val tables = listOf(
        T("departments", "Отделения", listOf("departmentId" to "ID отделения", "name" to "Название", "floor" to "Этаж", "phone" to "Телефон")),
        T("doctors", "Врачи", listOf("doctorId" to "ID врача", "departmentId" to "ID отделения", "fullName" to "ФИО", "specialization" to "Специализация", "phone" to "Телефон", "email" to "Email", "hireDate" to "Дата найма")),
        T("patients", "Пациенты", listOf("patientId" to "ID пациента", "fullName" to "ФИО", "birthDate" to "Дата рождения", "gender" to "Пол", "address" to "Адрес", "phone" to "Телефон", "policyNumber" to "Номер полиса ОМС")),
        T("wards", "Палаты", listOf("wardId" to "ID палаты", "departmentId" to "ID отделения", "wardNumber" to "Номер палаты", "capacity" to "Вместимость")),
        T("beds", "Койки", listOf("bedId" to "ID койки", "wardId" to "ID палаты", "bedNumber" to "Номер койки", "status" to "Статус")),
        T("diagnoses", "Диагнозы", listOf("diagnosisId" to "ID диагноза", "name" to "Название", "icdCode" to "Код МКБ")),
        T("hospitalizations", "Госпитализации", listOf("hospitalizationId" to "ID госпитализации", "patientId" to "ID пациента", "bedId" to "ID койки", "doctorId" to "ID врача", "diagnosisId" to "ID диагноза", "admissionDate" to "Дата поступления", "dischargeDate" to "Дата выписки", "status" to "Статус")),
        T("appointments", "Приёмы", listOf("appointmentId" to "ID приёма", "patientId" to "ID пациента", "doctorId" to "ID врача", "apptDatetime" to "Дата и время", "status" to "Статус", "reason" to "Причина", "room" to "Кабинет", "arrivedAt" to "Время прихода")),
        T("medical_records", "Мед. записи", listOf("recordId" to "ID записи", "patientId" to "ID пациента", "doctorId" to "ID врача", "diagnosisId" to "ID диагноза", "recordDate" to "Дата записи", "notes" to "Заметки")),
        T("prescriptions", "Назначения", listOf("prescriptionId" to "ID назначения", "recordId" to "ID записи", "medicationName" to "Лекарство", "dosage" to "Дозировка", "durationDays" to "Длительность, дней")),
        T("procedures", "Процедуры", listOf("procedureId" to "ID процедуры", "name" to "Название", "cost" to "Стоимость")),
        T("procedures_performed", "Выполн. процедуры", listOf("perfId" to "ID выполнения", "hospitalizationId" to "ID госпитализации", "procedureId" to "ID процедуры", "doctorId" to "ID врача", "perfDate" to "Дата выполнения")),
        T("lab_tests", "Анализы", listOf("testId" to "ID анализа", "patientId" to "ID пациента", "doctorId" to "ID врача", "testType" to "Тип анализа", "testDate" to "Дата", "result" to "Результат"))
    )

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it.code >= 32 || it == '\n' || it == '\t' }

    private fun strCell(ref: String, s: String, style: Int) = "<c r=\"$ref\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${esc(s)}</t></is></c>"

    // XML одного листа: первая строка шапка (жирная, закреплена), дальше данные
    private fun sheetXml(db: AppDb, t: T): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"$NS\">")
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        sb.append("<cols><col min=\"1\" max=\"${t.cols.size}\" width=\"24\" customWidth=\"1\"/></cols><sheetData>")
        sb.append("<row r=\"1\">")
        t.cols.forEachIndexed { i, c -> sb.append(strCell("${'A' + i}1", c.second, 1)) }
        sb.append("</row>")
        val cur = db.openHelper.readableDatabase.query("SELECT ${t.cols.joinToString(",") { it.first }} FROM ${t.table}")
        cur.use {
            var r = 2
            while (it.moveToNext()) {
                sb.append("<row r=\"$r\">")
                for (i in t.cols.indices) {
                    val ref = "${'A' + i}$r"
                    when (it.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> {}
                        Cursor.FIELD_TYPE_INTEGER -> sb.append("<c r=\"$ref\"><v>${it.getLong(i)}</v></c>")
                        Cursor.FIELD_TYPE_FLOAT -> sb.append("<c r=\"$ref\"><v>${it.getDouble(i)}</v></c>")
                        else -> sb.append(strCell(ref, it.getString(i), 0))
                    }
                }
                sb.append("</row>"); r++
            }
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private const val STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"$NS\">" +
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
        "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFD9E1F2\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/></cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>"

    // Собирает весь файл .xlsx в памяти
    fun build(db: AppDb): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            val head = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            val ct = StringBuilder("$head<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
            val wb = StringBuilder("$head<workbook xmlns=\"$NS\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>")
            val rels = StringBuilder("$head<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            tables.forEachIndexed { i, t ->
                val n = i + 1
                ct.append("<Override PartName=\"/xl/worksheets/sheet$n.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
                wb.append("<sheet name=\"${esc(t.sheet)}\" sheetId=\"$n\" r:id=\"rId$n\"/>")
                rels.append("<Relationship Id=\"rId$n\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$n.xml\"/>")
                put("xl/worksheets/sheet$n.xml", sheetXml(db, t))
            }
            val sid = tables.size + 1
            rels.append("<Relationship Id=\"rId$sid\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
            put("[Content_Types].xml", ct.append("</Types>").toString())
            put("_rels/.rels", "$head<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            put("xl/workbook.xml", wb.append("</sheets></workbook>").toString())
            put("xl/_rels/workbook.xml.rels", rels.toString())
            put("xl/styles.xml", STYLES)
        }
        return bos.toByteArray()
    }

    // Кнопка «Выгрузить»: сохраняет в «Загрузки» и открывает меню «Поделиться»
    suspend fun exportAndShare(ctx: Context, db: AppDb) {
        try {
            val name = "hospital_" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date()) + ".xlsx"
            val file = withContext(Dispatchers.IO) {
                val bytes = build(db)
                if (Build.VERSION.SDK_INT >= 29) {
                    val cv = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, MIME)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)?.let { uri ->
                        ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    }
                }
                File(ctx.cacheDir, name).also { it.writeBytes(bytes) }
            }
            Toast.makeText(ctx, if (Build.VERSION.SDK_INT >= 29) "Сохранено в «Загрузки»: $name" else "Файл готов: $name", Toast.LENGTH_LONG).show()
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = MIME; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Отправить файл Excel").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Ошибка выгрузки: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
