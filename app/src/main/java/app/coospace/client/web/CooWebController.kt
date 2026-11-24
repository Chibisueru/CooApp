package app.coospace.client.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.coospace.client.R

/**
 * Owns the single WebView that shows CooSpace, so the page (and login session) survives
 * tab switches. Exposes page state to Compose.
 */
@SuppressLint("SetJavaScriptEnabled")
class CooWebController(
    context: Context,
    private val onPageVisited: (title: String, url: String) -> Unit,
    private val onSessionDetected: (signedIn: Boolean) -> Unit,
) {
    var title by mutableStateOf("")
        private set
    var url by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var loading by mutableStateOf(false)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var error by mutableStateOf(false)
        private set
    var errorDetail by mutableStateOf("")
        private set

    /** Hosts that stay inside the app (CooSpace itself and SZTE / Microsoft sign-in pages). */
    private val internalHostSuffixes = listOf("u-szeged.hu", "szte.hu", "login.microsoftonline.com", "login.live.com")

    /** Set by the Activity: launches a file picker and reports the result back. */
    var launchFilePicker: ((Intent, (Array<Uri>?) -> Unit) -> Boolean)? = null

    private var styleJs: String? = null
    private var hasLoaded = false

    val webView: WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme ?: return false
                if (scheme == "http" || scheme == "https") {
                    val host = uri.host.orEmpty()
                    if (internalHostSuffixes.any { host == it || host.endsWith(".$it") }) return false
                }
                openExternally(uri)
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                this@CooWebController.loading = true
                this@CooWebController.error = false
                this@CooWebController.url = url
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                applyStyle()
            }

            override fun onPageFinished(view: WebView, url: String) {
                this@CooWebController.loading = false
                applyStyle()
                this@CooWebController.url = url
                this@CooWebController.title = view.title.orEmpty().let { if (it.startsWith("http")) "" else it }
                this@CooWebController.canGoBack = view.canGoBack()
                CookieManager.getInstance().flush()
                if (!this@CooWebController.error && this@CooWebController.title.isNotBlank()) onPageVisited(this@CooWebController.title, url)
                if (!this@CooWebController.error) detectSession(view)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                this@CooWebController.canGoBack = view.canGoBack()
                this@CooWebController.url = url
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                if (request.isForMainFrame) {
                    errorDetail = "${err.description} (${err.errorCode})\n${request.url}"
                    this@CooWebController.error = true
                    this@CooWebController.loading = false
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                this@CooWebController.progress = newProgress / 100f
            }

            override fun onReceivedTitle(view: WebView, t: String?) {
                if (!t.isNullOrBlank() && !t.startsWith("http")) this@CooWebController.title = t
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                val launcher = launchFilePicker ?: return false
                val intent = params.createIntent().apply {
                    if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return launcher(intent) { uris -> filePathCallback.onReceiveValue(uris) }
            }
        }

        setDownloadListener { dlUrl, userAgent, contentDisposition, mimeType, _ ->
            startDownload(context, dlUrl, userAgent, contentDisposition, mimeType)
        }
    }

    /**
     * Guesses whether the user is signed in from the loaded page:
     * a logout link means signed in; a visible password field (sign-in form) means signed out.
     * Pages that show neither (e.g. downloads, error pages) leave the state unchanged.
     */
    private fun detectSession(view: WebView) {
        val host = Uri.parse(view.url ?: return).host.orEmpty()
        if (internalHostSuffixes.none { host == it || host.endsWith(".$it") }) return
        view.evaluateJavascript(SESSION_JS) { result ->
            when (result?.trim('"')) {
                "in" -> onSessionDetected(true)
                "out" -> onSessionDetected(false)
            }
        }
    }

    fun loadIfNeeded(startUrl: String) {
        if (!hasLoaded) {
            hasLoaded = true
            webView.loadUrl(startUrl)
        }
    }

    fun load(target: String) {
        hasLoaded = true
        webView.loadUrl(target)
    }

    fun reload() { error = false; webView.reload() }
    fun goBack() { if (webView.canGoBack()) webView.goBack() }

    fun setTextZoom(percent: Int) { webView.settings.textZoom = percent }

    fun setStyle(js: String) {
        if (js == styleJs) return
        styleJs = js
        applyStyle()
    }

    private fun applyStyle() {
        styleJs?.let { webView.evaluateJavascript(it, null) }
    }

    fun signOut() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webView.clearCache(true)
        webView.clearHistory()
        hasLoaded = false
        title = ""
        url = null
        canGoBack = false
    }

    private fun openExternally(uri: Uri) {
        val ctx = webView.context
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // Nothing can handle it; ignore.
        }
    }

    private fun startDownload(context: Context, dlUrl: String, userAgent: String, disposition: String?, mime: String?) {
        val fileName = URLUtil.guessFileName(dlUrl, disposition, mime)
        val request = DownloadManager.Request(Uri.parse(dlUrl)).apply {
            setMimeType(mime)
            CookieManager.getInstance().getCookie(dlUrl)?.let { addRequestHeader("Cookie", it) }
            addRequestHeader("User-Agent", userAgent)
            setTitle(fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "CooSpace/$fileName")
        }
        runCatching {
            context.getSystemService(DownloadManager::class.java).enqueue(request)
            Toast.makeText(context, context.getString(R.string.download_started, fileName), Toast.LENGTH_SHORT).show()
        }.onFailure { openExternally(Uri.parse(dlUrl)) }
    }

    private companion object {
        val SESSION_JS = """
            (function(){
              function visible(e){ return !!(e.offsetWidth || e.offsetHeight || e.getClientRects().length); }
              var re = /logout|logoff|signout|sign-out|kijelentkez|kilep|kilép/i;
              var links = document.querySelectorAll('a, button, [onclick], form');
              for (var i = 0; i < links.length; i++) {
                var e = links[i];
                var s = (e.getAttribute('href') || '') + ' ' + (e.getAttribute('action') || '') + ' ' +
                        (e.getAttribute('onclick') || '') + ' ' + (e.textContent || '').slice(0, 60) + ' ' +
                        (e.getAttribute('title') || '') + ' ' + (e.getAttribute('aria-label') || '');
                if (re.test(s)) return 'in';
              }
              var pw = document.querySelectorAll('input[type=password]');
              for (var j = 0; j < pw.length; j++) if (visible(pw[j])) return 'out';
              return 'unknown';
            })();
        """.trimIndent()
    }

    fun destroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}
