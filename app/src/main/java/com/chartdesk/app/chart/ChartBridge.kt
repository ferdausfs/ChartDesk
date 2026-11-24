package com.chartdesk.app.chart

import android.webkit.JavascriptInterface

/**
 * The only JS -> Kotlin entry point. The page is a local asset and network access is blocked
 * in [ChartWebView], so this surface is not reachable by remote content.
 */
class ChartBridge(private val onMessage: (String) -> Unit) {

    @JavascriptInterface
    fun postMessage(raw: String) {
        onMessage(raw)
    }
}
