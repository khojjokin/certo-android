package com.qortn.certo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Calendar
import kotlin.math.abs

/**
 * Study nudges (local, no server):
 * - Streak at risk: 8pm, only if there's a streak and nothing studied today.
 * - Daily fact: 12:30, a short "Did you know?" from the current path's explanations.
 * - Come back: 2 days after the app was last closed.
 * Each one checks the latest progress when it fires, then schedules the next.
 */
object Nudges {
    private const val STREAK = 21
    private const val FACT = 22
    private const val COMEBACK = 23

    private fun prefs(c: Context) = c.getSharedPreferences("certo", Context.MODE_PRIVATE)
    fun enabled(c: Context, key: String) = prefs(c).getBoolean("nudge.$key", true)
    fun setEnabled(c: Context, key: String, on: Boolean) { prefs(c).edit().putBoolean("nudge.$key", on).apply(); scheduleAll(c) }

    /** Called when the app goes to the background and after settings change. */
    fun scheduleAll(c: Context) {
        schedule(c, STREAK, if (enabled(c, "streak")) nextAt(20, 0) else null)
        schedule(c, FACT, if (enabled(c, "facts")) nextAt(12, 30) else null)
        schedule(c, COMEBACK, if (enabled(c, "comeback")) Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 2); set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 30); set(Calendar.SECOND, 0)
        }.timeInMillis else null)
    }

    private fun nextAt(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    private fun schedule(c: Context, kind: Int, at: Long?) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(c, kind, Intent(c, NudgeReceiver::class.java).putExtra("kind", kind),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        if (at != null) am.set(AlarmManager.RTC_WAKEUP, at, pi)
    }

    internal fun fire(c: Context, kind: Int) {
        val app = c.applicationContext as CertoApp
        val store = app.store
        // Usually fired with the app closed: load the signed-in account's progress first (kept on the device).
        val uid = app.auth.clerk.user?.id
        store.ensureProfile(uid)
        val cert = store.currentCert
        val name = cert?.shortName ?: "your exam"
        val (title, body) = when (kind) {
            STREAK -> {
                schedule(c, STREAK, if (enabled(c, "streak")) nextAt(20, 0) else null)
                if (store.streak == 0 || store.todayCount > 0 || uid == null) return
                "🔥 Your ${store.streak}-day streak is at risk" to "Answer one $name question before midnight to keep it going."
            }
            FACT -> {
                schedule(c, FACT, if (enabled(c, "facts")) nextAt(12, 30) else null)
                val facts = cert?.let { store.availableQuestions(it).map { q -> q.explanation }.filter { e -> e.length in 40..170 } }.orEmpty()
                if (facts.isEmpty()) return
                val day = (System.currentTimeMillis() / 86_400_000L).toInt()
                "💡 Did you know?" to facts[abs(day * 31) % facts.size]
            }
            else -> "We miss you 👋" to "A quick 10 on $name takes 5 minutes. erha's ready with hints if you get stuck."
        }
        val open = PendingIntent.getActivity(c, kind, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CertoApp.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { c.getSystemService(android.app.NotificationManager::class.java).notify(kind, n) }
    }
}

class NudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Nudges.fire(context, intent.getIntExtra("kind", 0))
}
