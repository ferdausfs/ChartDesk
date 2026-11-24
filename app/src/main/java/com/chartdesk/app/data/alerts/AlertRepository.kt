package com.chartdesk.app.data.alerts

import com.chartdesk.app.data.db.AlertDao
import com.chartdesk.app.data.db.AlertEntity
import com.chartdesk.app.data.db.AlertEventEntity
import com.chartdesk.app.data.market.MarketRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class AlertRepository @Inject constructor(
    private val dao: AlertDao,
    private val market: MarketRepository,
    private val notifier: AlertNotifier,
) {

    val alerts: Flow<List<AlertEntity>> = dao.observeAll()
    val events: Flow<List<AlertEventEntity>> = dao.observeEvents()

    suspend fun create(symbol: String, direction: String, price: Double) {
        dao.upsert(
            AlertEntity(
                id = AlertEngine.newId(),
                symbol = symbol.trim().uppercase(),
                direction = direction,
                price = price,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: String) = dao.delete(id)

    /** Returns the number of alerts triggered by this check. */
    suspend fun checkNow(): Int {
        val active = dao.activeOnce()
        if (active.isEmpty()) return 0
        val prices = market.tickers(active.map { it.symbol }.distinct()).associate { it.symbol to it.lastPrice }
        val hits = AlertEngine.evaluate(active, prices)
        val now = System.currentTimeMillis()
        for (hit in hits) {
            dao.markTriggered(hit.alert.id, now)
            dao.insertEvent(
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
        return hits.size
    }

    suspend fun markEventsSeen() = dao.markEventsSeen()
}
