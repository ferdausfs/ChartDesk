package com.chartdesk.app.data.alerts

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chartdesk.app.data.db.AlertEventEntity
import com.chartdesk.app.data.db.ChartDeskDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Background check every 15 minutes. Uses the same Room file as the app; no Hilt worker factory needed,
 * which keeps the dependency graph small and the worker cheap.
 */
class AlertCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = Room.databaseBuilder(applicationContext, ChartDeskDatabase::class.java, "chartdesk.db").build()
        try {
            val alerts = db.alertDao().activeOnce()
            if (alerts.isEmpty()) return@withContext Result.success()
            val symbols = alerts.map { it.symbol }.distinct()
            val prices = fetchPrices(symbols)
            val hits = AlertEngine.evaluate(alerts, prices)
            val notifier = AlertNotifier(applicationContext)
            val now = System.currentTimeMillis()
            for (hit in hits) {
                db.alertDao().markTriggered(hit.alert.id, now)
                db.alertDao().insertEvent(
                    AlertEventEntity(
                        id = AlertEngine.newId(),
                        alertId = hit.alert.id,
                        symbol = hit.alert.symbol,
                        direction = hit.alert.direction,
                        target = hit.alert.price,
                        price = hit.price,
                        triggeredAt = now,
                    ),
                )
                notifier.notify(hit.alert.symbol, hit.alert.direction, hit.alert.price, hit.price)
            }
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        } finally {
            db.close()
        }
    }

    private fun fetchPrices(symbols: List<String>): Map<String, Double> {
        val encoded = symbols.joinToString("%22,%22", prefix = "%5B%22", postfix = "%22%5D")
        val request = Request.Builder()
            .url("https://api.binance.com/api/v3/ticker/price?symbols=$encoded")
            .build()
        val client = OkHttpClient.Builder().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyMap()
            val body = response.body?.string() ?: return emptyMap()
            val array = JSONArray(body)
            val map = HashMap<String, Double>()
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                map[row.getString("symbol")] = row.getString("price").toDoubleOrNull() ?: continue
            }
            return map
        }
    }
}
