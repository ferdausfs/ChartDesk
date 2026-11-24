package com.chartdesk.app.chart

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Captures the chart area from the window, including the hardware-accelerated WebView surface. */
object ScreenshotManager {

    suspend fun capture(activity: Activity, view: View): Bitmap? = suspendCoroutine { cont ->
        if (view.width <= 0 || view.height <= 0) {
            cont.resume(null)
            return@suspendCoroutine
        }
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val rect = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            activity.window,
            rect,
            bitmap,
            { result ->
                cont.resume(if (result == PixelCopy.SUCCESS) bitmap else null)
            },
            Handler(Looper.getMainLooper()),
        )
    }

    fun save(activity: Activity, bitmap: Bitmap): File {
        val dir = File(activity.getExternalFilesDir(null), "Pictures/ChartDesk").apply { mkdirs() }
        val file = File(dir, "chartdesk-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
}
