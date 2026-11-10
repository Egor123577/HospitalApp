package com.example.hospital.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.wear.compose.material.MaterialTheme
import com.example.hospital.wear.model.DemoData
import com.example.hospital.wear.model.QueueState
import com.example.hospital.wear.ui.VisitScreen

// БЛОК 2: экран врача на демо-данных. Жесты и вибрация добавятся в блоке 3.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state = remember { QueueState(DemoData.visits) }
                VisitScreen(state)
            }
        }
    }
}
