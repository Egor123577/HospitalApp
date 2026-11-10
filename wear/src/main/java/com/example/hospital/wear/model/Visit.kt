package com.example.hospital.wear.model

// Статус приёма пациента (см. PROJECT_PLAN.md)
enum class VisitStatus { WAITING, ARRIVED, DONE }

// Один приём у врача. Время хранится в минутах от полуночи (10:00 = 600).
data class Visit(
    val id: Long,
    val name: String,
    val birthYear: Int,
    val gender: String,
    val room: String,
    val reason: String,
    val scheduledMin: Int,
    val alert: String? = null,          // метка важного, например «Лейкоциты повышены»
    val status: VisitStatus = VisitStatus.WAITING,
    val arrivedMin: Int? = null         // время прихода пациента
)

fun Int.hhmm(): String = "%02d:%02d".format(this / 60, this % 60)
