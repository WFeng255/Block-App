package com.zhaocai.timesheet

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var chooser: ValueCallback<Array<Uri>>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val bg = Color.parseColor("#F2F5F7")
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        web = WebView(this).apply {
            setBackgroundColor(bg)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.textZoom = 100
            overScrollMode = View.OVER_SCROLL_NEVER
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams
                ): Boolean {
                    chooser?.onReceiveValue(null)
                    chooser = cb
                    return try {
                        startActivityForResult(p.createIntent(), 7); true
                    } catch (e: Exception) { chooser = null; false }
                }
            }
            addJavascriptInterface(Bridge(), "Android")
            loadUrl("file:///android_asset/so-gio-cong.html")
        }
        setContentView(web)
        applyTheme()
    }

    // Follow the system dark mode for the system bars, matching the app's own theme.
    private fun applyTheme() {
        val dark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val c = Color.parseColor(if (dark) "#0E1920" else "#F2F5F7")
        window.statusBarColor = c; window.navigationBarColor = c
        web.setBackgroundColor(c)
        var f = View.SYSTEM_UI_FLAG_VISIBLE
        if (!dark) f = f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = f
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == 7) {
            chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data))
            chooser = null
        } else super.onActivityResult(req, res, data)
    }

    inner class Bridge {
        @JavascriptInterface
        fun share(text: String) {
            runOnUiThread {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                }, "備份"))
            }
        }
    }

    override fun onBackPressed() { moveTaskToBack(true) }
}
