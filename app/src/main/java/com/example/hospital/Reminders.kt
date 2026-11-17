package com.example.hospital

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** Напоминания о приёме: будильник срабатывает за N минут до записи и показывает уведомление. */
object ReminderScheduler {
    private const val CHANNEL = "reminders"
    private const val PREFS = "settings"

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Напоминания о приёмах", NotificationManager.IMPORTANCE_HIGH)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, title: String, text: String, id: Int) {
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open).build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    private fun pending(ctx: Context, id: Long, title: String, text: String) = PendingIntent.getBroadcast(
        ctx, id.toInt(),
        Intent(ctx, ReminderReceiver::class.java).putExtra("title", title).putExtra("text", text).putExtra("id", id.toInt()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Пересоздаёт будильники по всем будущим приёмам (вызывается при изменении записей и настроек). */
    suspend fun syncAll(ctx: Context, db: AppDb) {
        val p = prefs(ctx)
        val enabled = p.getBoolean("remind", true); val lead = p.getInt("lead", 30)
        val docs = db.doctors().all().first().associateBy { it.doctorId }
        val pats = db.patients().all().first().associateBy { it.patientId }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val now = System.currentTimeMillis()
        val yesterday = fmt.format(java.util.Date(now - 86_400_000L))
        db.appointments().all().first().filter { it.apptDatetime >= yesterday }.forEach { a ->
            val doc = docs[a.doctorId]; val pat = pats[a.patientId]
            val text = "${pat?.fullName ?: "Пациент"}: ${doc?.fullName ?: "врач"}, каб. ${a.room ?: doc?.room ?: "—"}, в ${a.apptDatetime.takeLast(5)}"
            val pi = pending(ctx, a.appointmentId, "Напоминание о приёме", text)
            val trigger = (fmt.parse(a.apptDatetime)?.time ?: 0L) - lead * 60_000L
            if (enabled && a.status == "запланирован" && trigger > now) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            else am.cancel(pi)
        }
    }

    /** Показывает уведомление через несколько секунд (для демонстрации). */
    fun showLater(ctx: Context, delayMs: Long) {
        val app = ctx.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({ show(app, "Напоминание о приёме", "Демо: приём через ${prefs(app).getInt("lead", 30)} минут", 2) }, delayMs)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        ReminderScheduler.show(ctx, intent.getStringExtra("title") ?: "Приём", intent.getStringExtra("text") ?: "", intent.getIntExtra("id", 0))
    }
}

/** Карточка настроек напоминаний (на вкладке «Запись»). */
@Composable
fun ReminderSettings(db: AppDb) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ReminderScheduler.prefs(ctx) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("remind", true)) }
    var lead by remember { mutableIntStateOf(prefs.getInt("lead", 30)) }
    fun resync() { scope.launch { ReminderScheduler.syncAll(ctx, db) } }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Напоминать о приёме", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Switch(enabled, { enabled = it; prefs.edit().putBoolean("remind", it).apply(); resync() })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60).forEach { m ->
                    FilterChip(lead == m, { lead = m; prefs.edit().putInt("lead", m).apply(); resync() }, label = { Text("за $m мин") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ ReminderScheduler.show(ctx, "Напоминание о приёме", "Проверка: уведомления работают", 1) }) { Text("Проверить", fontSize = 13.sp) }
                OutlinedButton({ ReminderScheduler.showLater(ctx, 10_000) }) { Text("Через 10 сек", fontSize = 13.sp) }
            }
        }
    }
}
