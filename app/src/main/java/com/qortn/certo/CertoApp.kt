package com.qortn.certo

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.qortn.certo.auth.AuthStore
import com.qortn.certo.billing.Billing
import com.qortn.certo.data.StudyStore
import com.qortn.certo.net.CertoApi
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar

class CertoApp : Application() {
    lateinit var auth: AuthStore
    lateinit var store: StudyStore
    lateinit var billing: Billing
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pushJob: Job? = null
    private var applyingRemote = false
    /** When the app last went to the background (for the 10-minute splash). */
    var leftAt: Long = 0

    override fun onCreate() {
        super.onCreate()
        SentryAndroid.init(this) { o ->
            o.dsn = "https://31493994d54b76c4f8af12f7024127bb@o4512218382467072.ingest.de.sentry.io/4512299365040208"
            o.tracesSampleRate = 0.2
            o.isSendDefaultPii = false
            o.environment = if (BuildConfig.DEBUG) "debug" else "production"
            o.isAnrEnabled = true                 // app freezes ("not responding")
            o.isAttachThreads = true
            o.isEnableUserInteractionBreadcrumbs = true
        }
        auth = AuthStore(this)
        store = StudyStore(this)
        billing = Billing(this)
        billing.start()
        store.onSave = { json -> schedulePush(json) }
        createChannel()
        scope.launch { CertoApi.subscriptionsEnabled()?.let { store.applySubscriptionsEnabled(it) } }
    }

    /** After sign-in: download the account's progress, or upload this device's if the account has none. */
    suspend fun pull() {
        val token = auth.token() ?: return
        runCatching {
            val remote = CertoApi.fetchProgress(token)
            if (remote != null) {
                applyingRemote = true
                store.applyRemote(remote)
                applyingRemote = false
            } else {
                CertoApi.saveProgress(token, store.encoded())
            }
        }
        applyingRemote = false
    }

    private fun schedulePush(json: JSONObject) {
        if (applyingRemote || auth.user == null) return
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(2000)
            val token = auth.token() ?: return@launch
            runCatching { CertoApi.saveProgress(token, json) }
        }
    }

    // MARK: Daily reminder

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Study reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun applyReminder() {
        val am = getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            this, 1, Intent(this, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        val d = store.data
        if (!d.remindersOn) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, d.reminderHour); set(Calendar.MINUTE, d.reminderMinute); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    private var memoryReported = false

    /** Reports critical low memory to Sentry (once per launch) so leaks show up in the admin console. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL && level < TRIM_MEMORY_UI_HIDDEN && !memoryReported) {
            memoryReported = true
            val rt = Runtime.getRuntime()
            val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
            io.sentry.Sentry.captureMessage("Low memory warning", io.sentry.SentryLevel.WARNING)
            io.sentry.Sentry.configureScope { it.setExtra("heap_used_mb", usedMb.toString()) }
        }
    }

    companion object {
        const val CHANNEL = "certo.daily"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as CertoApp
        val cert = app.store.currentCert?.shortName ?: "your cert"
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CertoApp.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to study")
            .setContentText("A quick 10 on $cert keeps your streak alive.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(android.app.NotificationManager::class.java).notify(1, n) }
    }
}
