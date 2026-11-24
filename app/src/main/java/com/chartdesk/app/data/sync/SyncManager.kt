package com.chartdesk.app.data.sync

import androidx.room.withTransaction
import com.chartdesk.app.data.db.AlertEntity
import com.chartdesk.app.data.db.ChartDeskDatabase
import com.chartdesk.app.data.db.DrawingEntity
import com.chartdesk.app.data.db.IndicatorEntity
import com.chartdesk.app.data.db.LayoutEntityDb
import com.chartdesk.app.data.db.ScriptEntity
import com.chartdesk.app.data.db.WatchlistEntity
import com.chartdesk.app.data.db.WatchlistItemEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Portable sync bundle: every user-owned row in one JSON document. Works over any file channel (SAF, Drive,
 * email) until the Worker sync endpoints are deployed.
 */
@Singleton
class SyncManager @Inject constructor(
    private val db: ChartDeskDatabase,
    private val json: Json,
) {

    suspend fun exportJson(): String {
        val payload = SyncPayload(
            exportedAt = System.currentTimeMillis(),
            watchlists = db.watchlistDao().listsOnce().map { WatchlistDto(it.id, it.name, it.sortOrder, it.createdAt) },
            items = db.watchlistDao().itemsAll().map { WatchlistItemDto(it.listId, it.symbol, it.sortOrder) },
            layouts = db.layoutDao().allOnce().map {
                LayoutDto(it.id, it.name, it.symbol, it.timeframe, it.chartType, it.grid, it.createdAt)
            },
            scripts = db.scriptDao().allOnce().map { ScriptDto(it.id, it.name, it.code, it.updatedAt, it.builtIn) },
            drawings = db.drawingDao().allOnce().map {
                DrawingDtoSync(it.symbol, it.id, it.type, it.payloadJson, it.locked, it.sortOrder, it.updatedAt)
            },
            indicators = db.indicatorDao().allOnce().map {
                IndicatorDtoSync(it.symbol, it.uid, it.type, it.paramsJson, it.visible, it.sortOrder)
            },
            alerts = db.alertDao().allOnce().map {
                AlertDtoSync(it.id, it.symbol, it.direction, it.price, it.active, it.createdAt, it.triggeredAt)
            },
        )
        return json.encodeToString(payload)
    }

    suspend fun importJson(raw: String): Result<Int> {
        return runCatching {
            val payload = json.decodeFromString<SyncPayload>(raw)
            var imported = 0
            db.withTransaction {
                val watchlistDao = db.watchlistDao()
                payload.watchlists.forEach {
                    watchlistDao.upsertList(WatchlistEntity(it.id, it.name, it.sortOrder, it.createdAt))
                    imported++
                }
                if (payload.items.isNotEmpty()) {
                    watchlistDao.upsertItems(payload.items.map { WatchlistItemEntity(it.listId, it.symbol, it.sortOrder) })
                    imported += payload.items.size
                }
                val layoutDao = db.layoutDao()
                payload.layouts.forEach {
                    layoutDao.upsert(LayoutEntityDb(it.id, it.name, it.symbol, it.timeframe, it.chartType, it.grid, it.createdAt))
                    imported++
                }
                val scriptDao = db.scriptDao()
                payload.scripts.forEach {
                    scriptDao.upsert(ScriptEntity(it.id, it.name, it.code, it.updatedAt, it.builtIn))
                    imported++
                }
                val drawingDao = db.drawingDao()
                if (payload.drawings.isNotEmpty()) {
                    drawingDao.upsert(payload.drawings.map {
                        DrawingEntity(it.symbol, it.id, it.type, it.payloadJson, it.locked, it.sortOrder, it.updatedAt)
                    })
                    imported += payload.drawings.size
                }
                val indicatorDao = db.indicatorDao()
                if (payload.indicators.isNotEmpty()) {
                    indicatorDao.upsert(payload.indicators.map {
                        IndicatorEntity(it.symbol, it.uid, it.type, it.paramsJson, it.visible, it.sortOrder)
                    })
                    imported += payload.indicators.size
                }
                val alertDao = db.alertDao()
                payload.alerts.forEach {
                    alertDao.upsert(AlertEntity(it.id, it.symbol, it.direction, it.price, it.active, it.createdAt, it.triggeredAt))
                    imported++
                }
            }
            imported
        }
    }
}
