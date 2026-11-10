package com.example.hospital.wear.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Состояние очереди. Его меняют и касания, и жесты (блок 3), поэтому логика собрана в одном месте.
class QueueState(initial: List<Visit>) {
    var visits by mutableStateOf(initial)
    var index by mutableIntStateOf(0)
    var focus by mutableIntStateOf(0)       // 0 = верхняя кнопка, 1 = нижняя

    val current: Visit get() = visits[index]

    fun next() { index = (index + 1) % visits.size; focus = 0 }
    fun toggleFocus() { focus = (focus + 1) % 2 }

    private fun update(v: Visit) { visits = visits.map { if (it.id == v.id) v else it } }

    // ВРЕМЕННО (блок 2): отметка прихода по времени записи. В блоке 4 берётся время часов.
    fun primaryAction() {
        val v = current
        when (v.status) {
            VisitStatus.WAITING -> update(v.copy(status = VisitStatus.ARRIVED, arrivedMin = v.scheduledMin))
            VisitStatus.ARRIVED -> update(v.copy(status = VisitStatus.DONE))
            VisitStatus.DONE -> Unit
        }
    }
}
