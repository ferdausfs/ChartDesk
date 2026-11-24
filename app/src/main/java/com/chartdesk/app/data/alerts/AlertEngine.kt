package com.chartdesk.app.data.alerts

import com.chartdesk.app.data.db.AlertEntity
import java.util.UUID

data class AlertHit(
    val alert: AlertEntity,
    val price: Double,
)

/** Pure alert evaluation shared by the in-app poller and the background worker. */
object AlertEngine {

    fun isHit(alert: AlertEntity, price: Double): Boolean = when (alert.direction) {
        "above" -> price >= alert.price
        "below" -> price <= alert.price
        else -> false
    }

    fun evaluate(alerts: List<AlertEntity>, prices: Map<String, Double>): List<AlertHit> {
        val hits = ArrayList<AlertHit>()
        for (alert in alerts) {
            if (!alert.active) continue
            val price = prices[alert.symbol] ?: continue
            if (isHit(alert, price)) hits += AlertHit(alert, price)
        }
        return hits
    }

    fun newId(): String = UUID.randomUUID().toString()
}
