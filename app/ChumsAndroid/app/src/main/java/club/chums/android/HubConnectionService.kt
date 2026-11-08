package club.chums.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import android.os.IBinder
import androidx.core.app.NotificationCompat
import club.chums.android.data.HubClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** User-enabled foreground connection. Android shows a persistent notification while polling. */
class HubConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var hub: HubClient? = null
    private var observer: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("connection", "Hub connection", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel("agents", "Agent requests and replies", NotificationManager.IMPORTANCE_DEFAULT))
        startForeground(1, notification("connection", "Background connection enabled", "Open Chums to view connection status and your agents."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        observer?.cancel()
        hub?.close()
        val client = HubClient(this)
        hub = client
        client.start()
        observer = scope.launch {
            val seenApprovals = mutableSetOf<String>()
            val seenReplies = mutableSetOf<String>()
            var first = true
            client.hubState.collect { state ->
                if (state == null) return@collect
                state.approvals.forEach { request ->
                    if (seenApprovals.add(request.requestId) && !MainActivity.inForeground) {
                        notifyAgent("approval", request.requestId.hashCode(),
                            notification("agents", "${request.session} needs approval", request.description))
                    }
                }
                state.spoken.forEach { line ->
                    val fresh = seenReplies.add(line.id)
                    if (fresh && !first && !MainActivity.inForeground && line.kind in setOf("done", "blocked", "question")) {
                        notifyAgent("reply", line.id.hashCode(),
                            notification("agents", "${line.agent ?: line.session} · ${line.kind}", line.text))
                    }
                }
                val currentRequests = state.approvals.map { it.requestId }.toSet()
                (seenApprovals - currentRequests).forEach { getSystemService(NotificationManager::class.java).cancel("approval", it.hashCode()) }
                seenApprovals.retainAll(currentRequests)
                if (seenReplies.size > 1000) seenReplies.retainAll(state.spoken.map { it.id }.toSet())
                first = false
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(channel: String, title: String, text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(channel != "connection")
            .setOngoing(channel == "connection")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    private fun notifyAgent(tag: String, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        getSystemService(NotificationManager::class.java).notify(tag, id, notification)
    }

    override fun onDestroy() {
        observer?.cancel()
        hub?.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences("chums_prefs", Context.MODE_PRIVATE).edit().putBoolean("background_connection", enabled).apply()
            val intent = Intent(context, HubConnectionService::class.java)
            if (enabled) androidx.core.content.ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
        }
    }
}
