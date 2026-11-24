package com.chartdesk.app.chart

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

private const val CHART_URL = "file:///android_asset/chart/index.html"
private const val ASSET_PREFIX = "file:///android_asset/chart/"

/**
 * Hosts the locally bundled chart page. Network access is disabled at the WebView level:
 * market data reaches the chart through the Kotlin bridge, never from inside the page.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChartWebView(controller: ChartController, modifier: Modifier = Modifier) {
    DisposableEffect(controller) {
        onDispose { controller.detach() }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                overScrollMode = WebView.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    mediaPlaybackRequiresUserGesture = true
                }
                webViewClient = LocalOnlyClient()
                addJavascriptInterface(ChartBridge { raw -> controller.onJsMessage(raw) }, "AndroidBridge")
                controller.attach(this)
                loadUrl(CHART_URL)
            }
        },
        onRelease = { view ->
            controller.detach()
            view.removeJavascriptInterface("AndroidBridge")
            view.loadUrl("about:blank")
            view.destroy()
        },
    )
}

private class LocalOnlyClient : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (url.startsWith(ASSET_PREFIX) || url.startsWith("data:") || url.startsWith("about:")) {
            return super.shouldInterceptRequest(view, request)
        }
        // Anything that is not a bundled asset is refused.
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
}
