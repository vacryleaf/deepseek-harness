package ai.deepseek.dsh.android

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Android shell that loads a remotely hosted DSH Web application. */
class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private var remoteWebView: WebView? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var currentServiceUrl: String? = null
    private val serviceStore by lazy {
        ServiceStore(getSharedPreferences(PREFERENCES, MODE_PRIVATE))
    }
    private val taskCompletionNotifier by lazy { TaskCompletionNotifier(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        val lastService = serviceStore.last()
        if (lastService == null) showSetup() else showWeb(lastService)
    }

    private fun showSetup(message: String? = null, initialUrl: String? = currentServiceUrl) {
        DshKeepAliveService.stop(this)
        destroyWebView()
        content = FrameLayout(this)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        scroll.addView(page, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        val title = TextView(this).apply {
            text = getString(R.string.setup_title)
            textSize = 28f
            setTextColor(Color.rgb(24, 30, 42))
        }
        page.addView(title, matchWidthWrap())

        val description = TextView(this).apply {
            text = getString(R.string.setup_description)
            textSize = 16f
            setTextColor(Color.rgb(84, 91, 105))
            setPadding(0, dp(12), 0, dp(24))
        }
        page.addView(description, matchWidthWrap())

        val input = EditText(this).apply {
            hint = getString(R.string.service_url_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(initialUrl ?: savedServiceUrl())
            setSelectAllOnFocus(false)
        }

        val error = TextView(this).apply {
            text = message.orEmpty()
            textSize = 14f
            setTextColor(Color.rgb(177, 48, 48))
            setPadding(0, dp(12), 0, 0)
            visibility = if (message.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        val savedServices = serviceStore.all()
        if (savedServices.isNotEmpty()) {
            val savedTitle = TextView(this).apply {
                text = getString(R.string.saved_services)
                textSize = 17f
                setTextColor(Color.rgb(24, 30, 42))
                setPadding(0, 0, 0, dp(8))
            }
            page.addView(savedTitle, matchWidthWrap())
            savedServices.forEach { url ->
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                val service = TextView(this).apply {
                    text = serviceDisplayName(url)
                    textSize = 15f
                    maxLines = 2
                    setTextColor(Color.rgb(46, 53, 68))
                    setOnClickListener { input.setText(url) }
                }
                row.addView(service, LinearLayout.LayoutParams(0, dp(52), 1f))
                val use = Button(this).apply {
                    text = getString(R.string.use_service)
                    setOnClickListener { connectToService(url, error) }
                }
                row.addView(use, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(52),
                ))
                val remove = Button(this).apply {
                    text = getString(R.string.delete_service)
                    setOnClickListener {
                        serviceStore.remove(url)
                        showSetup(initialUrl = serviceStore.last())
                    }
                }
                row.addView(remove, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(52),
                ))
                page.addView(row, matchWidthWrap())
            }
            val separator = View(this).apply {
                setBackgroundColor(Color.rgb(225, 228, 234))
            }
            page.addView(separator, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1),
            ).apply { topMargin = dp(12); bottomMargin = dp(20) })
        }

        page.addView(input, matchWidthWrap())
        page.addView(error, matchWidthWrap())

        val connect = Button(this).apply {
            text = getString(R.string.save_and_connect)
            setOnClickListener { connectToService(input.text.toString(), error) }
        }
        val connectParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        connectParams.topMargin = dp(24)
        page.addView(connect, connectParams)

        val hint = TextView(this).apply {
            text = if (BuildConfig.DEBUG) {
                getString(R.string.setup_hint_debug)
            } else {
                getString(R.string.setup_hint_release)
            }
            textSize = 13f
            setTextColor(Color.rgb(112, 118, 130))
            setPadding(0, dp(18), 0, 0)
        }
        page.addView(hint, matchWidthWrap())

        content.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        setContentView(content)
    }

    private fun connectToService(rawUrl: String, error: TextView) {
        val validation = ServiceUrlPolicy.validate(rawUrl, BuildConfig.DEBUG)
        val url = validation.url
        if (url == null) {
            error.text = validationError(validation.error)
            error.visibility = View.VISIBLE
            return
        }
        saveServiceUrl(url)
        showWeb(url)
    }

    private fun showWeb(url: String) {
        currentServiceUrl = url
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(8), dp(4))
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        val label = TextView(this).apply {
            text = getString(R.string.remote_service, serviceDisplayName(url))
            textSize = 16f
            setTextColor(Color.rgb(24, 30, 42))
        }
        toolbar.addView(label, LinearLayout.LayoutParams(0, dp(48), 1f))
        val change = Button(this).apply {
            text = getString(R.string.change_service)
            setOnClickListener { showSetup(initialUrl = currentServiceUrl) }
        }
        toolbar.addView(change, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(48),
        ))
        page.addView(toolbar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(56),
        ))

        val web = WebView(this)
        remoteWebView = web
        web.addJavascriptInterface(TaskCompletionBridge { completion ->
            runOnUiThread {
                if (remoteWebView === web) {
                    taskCompletionNotifier.notifyTaskCompleted(url, completion)
                }
            }
        }, ANDROID_BRIDGE)
        configureWebView(web)
        page.addView(web, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
        setContentView(page)
        taskCompletionNotifier.ensureChannel()
        requestNotificationPermissionIfNeeded()
        DshKeepAliveService.start(this, url)
        web.loadUrl(url)
    }

    private fun configureWebView(web: WebView) {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowContentAccess = false
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(false)
            userAgentString = "$userAgentString DSH-Android/${BuildConfig.VERSION_NAME}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                if (request.isForMainFrame && (scheme == "http" || scheme == "https")) return false
                return openExternal(request.url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame && remoteWebView === view) {
                    showSetup(getString(R.string.connection_failed), currentServiceUrl)
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: android.webkit.WebResourceResponse,
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request.isForMainFrame && errorResponse.statusCode >= 400 && remoteWebView === view) {
                    showSetup(getString(R.string.http_error, errorResponse.statusCode), currentServiceUrl)
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError,
            ) {
                handler.cancel()
                if (remoteWebView === view) showSetup(getString(R.string.tls_error), currentServiceUrl)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST)
                    true
                } catch (_: ActivityNotFoundException) {
                    filePathCallback = null
                    callback.onReceiveValue(null)
                    Toast.makeText(this@MainActivity, R.string.file_picker_failed, Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        if (preferences.getBoolean(NOTIFICATION_PERMISSION_REQUESTED, false)) return
        preferences.edit().putBoolean(NOTIFICATION_PERMISSION_REQUESTED, true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
    }

    private fun openExternal(uri: Uri): Boolean {
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.external_link_failed, Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun validationError(error: ServiceUrlPolicy.Error?): String {
        return when (error) {
            ServiceUrlPolicy.Error.HTTPS_REQUIRED -> getString(R.string.https_required)
            ServiceUrlPolicy.Error.ORIGIN_ONLY -> getString(R.string.url_must_be_origin)
            ServiceUrlPolicy.Error.INVALID_URL, null -> getString(R.string.invalid_url)
        }
    }

    private fun savedServiceUrl(): String? = serviceStore.last()

    private fun saveServiceUrl(url: String) {
        currentServiceUrl = url
        serviceStore.remember(url)
    }

    private fun serviceDisplayName(url: String): String {
        return Uri.parse(url).authority ?: url
    }

    private fun destroyWebView() {
        remoteWebView?.let { web ->
            web.stopLoading()
            web.removeJavascriptInterface(ANDROID_BRIDGE)
            web.webChromeClient = null
            web.destroy()
        }
        remoteWebView = null
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
    }

    private fun matchWidthWrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onBackPressed() {
        val web = remoteWebView
        if (web?.canGoBack() == true) {
            web.goBack()
        } else {
            DshKeepAliveService.stop(this)
            super.onBackPressed()
        }
    }

    @Deprecated("Android activity result API is retained for WebView file chooser compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_CHOOSER_REQUEST) return
        val result = if (resultCode == RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
        filePathCallback?.onReceiveValue(result)
        filePathCallback = null
    }

    override fun onDestroy() {
        destroyWebView()
        super.onDestroy()
    }

    private companion object {
        const val PREFERENCES = "dsh_android"
        const val ANDROID_BRIDGE = "DshAndroidBridge"
        const val FILE_CHOOSER_REQUEST = 1001
        const val NOTIFICATION_PERMISSION_REQUEST = 1002
        const val NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}
