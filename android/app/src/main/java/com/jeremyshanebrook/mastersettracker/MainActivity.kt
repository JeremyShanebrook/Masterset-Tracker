package com.jeremyshanebrook.mastersettracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.security.MessageDigest
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView

    companion object {
        private const val GITHUB_OWNER = "JeremyShanebrook"
        private const val GITHUB_REPO = "Masterset-Tracker"
    }

    private var updateCheckDone = false
    // Current view inside the single-page app, reported by the JS (home /
    // binder / scan / modal). The phone back button uses it instead of the
    // WebView history, which never changes in a single-page app.
    private var currentView: String = "home"

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null

    // Disk cache for card pictures. The binder re-creates its image tags on
    // every page turn, so without this each turn re-fetches the pictures
    // from the internet. First view downloads once; after that the picture
    // comes off the disk, so turning pages is instant.
    private val imageCacheHosts = setOf(
        "images.scrydex.com", "images.pokemontcg.io", "www.pokemon.com",
        "assets.tcgdex.net", "efour.b-cdn.net", "images.dexli.st"
    )
    private val imageCacheMaxBytes = 250L * 1024 * 1024

    private fun imageCacheFile(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(cacheDir, "image-cache").apply { mkdirs() }, hash)
    }

    private fun mimeFor(url: String, contentType: String?): String {
        if (!contentType.isNullOrBlank()) return contentType.split(";")[0].trim()
        val base = url.substringBefore("?")
        return when {
            base.endsWith(".png", ignoreCase = true) -> "image/png"
            base.endsWith(".jpg", ignoreCase = true) || base.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            base.endsWith(".webp", ignoreCase = true) -> "image/webp"
            base.endsWith(".gif", ignoreCase = true) -> "image/gif"
            else -> "image/png"
        }
    }

    private fun pruneImageCache() {
        try {
            val dir = File(cacheDir, "image-cache")
            val files = (dir.listFiles() ?: return).filter { !it.name.endsWith(".mime") && !it.name.endsWith(".tmp") }
            var total = files.sumOf { it.length() }
            if (total <= imageCacheMaxBytes) return
            files.sortedBy { it.lastModified() }.forEach { f ->
                if (total <= imageCacheMaxBytes) return@forEach
                total -= f.length()
                File(dir, f.name + ".mime").delete()
                f.delete()
            }
        } catch (_: Exception) {
        }
    }

    // Serve a card picture from the disk cache, downloading it once on a miss.
    // Returns null on any failure so the WebView falls back to its own fetch.
    private fun cachedImageResponse(url: String): WebResourceResponse? {
        return try {
            val file = imageCacheFile(url)
            if (file.exists() && file.length() > 0) {
                val mimeFile = File(file.parentFile, file.name + ".mime")
                val mime = if (mimeFile.exists()) mimeFile.readText().trim().ifEmpty { null } else null
                return WebResourceResponse(mimeFor(url, mime), "UTF-8", file.inputStream())
            }
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Masterset-Tracker")
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val contentType = conn.contentType
            val tmp = File(file.parentFile, file.name + ".tmp-" + System.nanoTime())
            conn.inputStream.use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
            conn.disconnect()
            if (tmp.length() > 0) {
                tmp.renameTo(file)
                if (!contentType.isNullOrBlank()) {
                    File(file.parentFile, file.name + ".mime").writeText(contentType.split(";")[0].trim())
                }
                pruneImageCache()
                return WebResourceResponse(mimeFor(url, contentType), "UTF-8", file.inputStream())
            }
            tmp.delete()
            null
        } catch (_: Exception) {
            null
        }
    }

    // The in-app scan camera (getUserMedia) asks the WebView for the camera.
    // If the app doesn't hold the runtime permission yet, ask for it first
    // and then grant or deny the WebView request from the answer.
    private var pendingPermissionRequest: PermissionRequest? = null
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val req = pendingPermissionRequest
        pendingPermissionRequest = null
        if (granted) req?.grant(req.resources) else req?.deny()
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = fileChooserCallback
        fileChooserCallback = null
        if (result.resultCode == Activity.RESULT_OK) {
            val dataUri = result.data?.data
            val uris = when {
                dataUri != null -> arrayOf(dataUri)
                cameraPhotoUri != null -> arrayOf(cameraPhotoUri!!)
                else -> emptyArray()
            }
            cb?.onReceiveValue(uris)
        } else {
            cb?.onReceiveValue(null)
        }
        cameraPhotoUri = null
    }

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
            webChromeClient = object : WebChromeClient() {
                // Lets the in-app scan camera (getUserMedia) use the camera.
                override fun onPermissionRequest(request: PermissionRequest) {
                    if (!request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        request.deny()
                        return
                    }
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        request.grant(request.resources)
                    } else {
                        pendingPermissionRequest?.deny()
                        pendingPermissionRequest = request
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
                // Lets <input type="file"> (the Scan-a-card camera input) open a
                // chooser with the camera and the gallery.
                override fun onShowFileChooser(
                    view: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = filePathCallback
                    val photoFile = try {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val dir = File(cacheDir, "camera").apply { mkdirs() }
                        File.createTempFile("scan_$stamp", ".jpg", dir)
                    } catch (_: Exception) { null }
                    cameraPhotoUri = photoFile?.let {
                        FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", it)
                    }
                    val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        cameraPhotoUri?.let { putExtra(MediaStore.EXTRA_OUTPUT, it) }
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    val chooser = Intent.createChooser(contentIntent, "Take or choose a photo").apply {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
                    }
                    return try {
                        fileChooserLauncher.launch(chooser)
                        true
                    } catch (_: Exception) {
                        fileChooserCallback = null
                        false
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return request.url.scheme != "file" && request.url.scheme != "about"
                }
                // Card pictures come from the disk cache (see cachedImageResponse)
                // so page turns don't re-fetch them from the internet.
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.url.host in imageCacheHosts) {
                        cachedImageResponse(request.url.toString())?.let { return it }
                    }
                    return super.shouldInterceptRequest(view, request)
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
                when (currentView) {
                    // Card info open: close it, stay in the binder.
                    "modal" -> webView.evaluateJavascript("closeModal()", null)
                    // Inside a binder or the scanner: back to the binder list.
                    "binder", "scan" -> webView.evaluateJavascript("goHome()", null)
                    // Already home: ask before exiting.
                    else -> {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Exit Masterset Tracker?")
                            .setMessage("Would you like to exit the app?")
                            .setNegativeButton("Stay", null)
                            .setPositiveButton("Exit") { _, _ -> finish() }
                            .show()
                    }
                }
            }
        })
    }

    private inner class AndroidBridge {
        @JavascriptInterface fun versionName(): String = currentVersionName()
        @JavascriptInterface fun versionCode(): Int = currentVersionCode()
        @JavascriptInterface fun checkForUpdates() { checkForUpdatesInternal(silent = false) }
        // Called by the page whenever the view changes, so the phone back
        // button knows where the user is inside the single-page app.
        @JavascriptInterface fun setView(state: String) { currentView = state }
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
