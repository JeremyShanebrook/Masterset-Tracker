package com.jeremyshanebrook.mastersettracker

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView

    companion object {
        private const val GITHUB_OWNER = "JeremyShanebrook"
        private const val GITHUB_REPO = "Masterset-Tracker"
    }

    private var updateCheckDone = false

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.rgb(11, 31, 51)
        window.navigationBarColor = Color.rgb(11, 31, 51)

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(11, 31, 51))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.textZoom = 100
            addJavascriptInterface(AndroidBridge(), "AndroidApp")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return request.url.scheme != "file" && request.url.scheme != "about"
                }
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (!updateCheckDone) {
                        updateCheckDone = true
                        postDelayed({ checkForUpdatesInternal(silent = true) }, 1200)
                    }
                }
            }
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private inner class AndroidBridge {
        @JavascriptInterface fun versionName(): String = currentVersionName()
        @JavascriptInterface fun versionCode(): Int = currentVersionCode()
        @JavascriptInterface fun checkForUpdates() { checkForUpdatesInternal(silent = false) }
    }

    private fun currentVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
    } catch (_: Exception) { "1.0" }

    private fun currentVersionCode(): Int = try {
        packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
    } catch (_: Exception) { 0 }

    private fun latestReleaseUrl(): String =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private fun releaseVersion(tag: String): String = tag.removePrefix("v").trim()

    private fun releaseApkUrl(release: JSONObject): String {
        val assets = release.optJSONArray("assets") ?: return ""
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name", "").lowercase().endsWith(".apk")) {
                return asset.optString("browser_download_url", "")
            }
        }
        return ""
    }

    private fun checkForUpdatesInternal(silent: Boolean = false) {
        thread {
            try {
                val conn = URL(latestReleaseUrl()).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                conn.setRequestProperty("User-Agent", "Masterset-Tracker")
                val code = conn.responseCode
                if (code !in 200..299) throw IllegalStateException("GitHub returned HTTP $code")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val release = JSONObject(body)
                if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
                    if (!silent) runOnUiThread { showMessage("You're up to date.\nVersion ${currentVersionName()}") }
                    return@thread
                }
                val remoteName = releaseVersion(release.optString("tag_name", ""))
                val notes = release.optString("body", "")
                val apkUrl = releaseApkUrl(release)
                val current = currentVersionName()
                runOnUiThread {
                    if (remoteName.isNotBlank() && remoteName != current && apkUrl.isNotBlank()) {
                        AlertDialog.Builder(this)
                            .setTitle("Update available")
                            .setMessage("Version $remoteName is available.\n\n$notes")
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Download & Install") { _, _ -> downloadAndInstall(apkUrl, remoteName) }
                            .show()
                    } else if (!silent) {
                        showMessage("You're up to date.\nVersion ${currentVersionName()}")
                    }
                }
            } catch (e: Exception) {
                if (!silent) runOnUiThread {
                    showMessage("Couldn't check GitHub for updates.\n\n${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    private fun downloadAndInstall(apkUrl: String, version: String) {
        thread {
            try {
                val dir = File(cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, "Masterset-Tracker-$version.apk")
                val conn = URL(apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                val code = conn.responseCode
                if (code !in 200..299) {
                    conn.disconnect()
                    throw IllegalStateException("GitHub returned HTTP $code while downloading the APK")
                }
                conn.inputStream.use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
                conn.disconnect()
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
                runOnUiThread {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { startActivity(intent) } catch (_: Exception) {
                        showMessage("Android blocked the installer. Allow this app to install unknown apps, then try again.")
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { showMessage("The update download failed.") }
            }
        }
    }

    private fun showMessage(message: String) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
