package com.chartdesk.app.data.drawings

import androidx.room.withTransaction
import com.chartdesk.app.data.db.ChartDeskDatabase
import com.chartdesk.app.data.db.DrawingDao
import com.chartdesk.app.data.db.DrawingEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class DrawingRepository @Inject constructor(
    private val db: ChartDeskDatabase,
    private val dao: DrawingDao,
    private val json: Json,
) {

    fun observe(symbol: String): Flow<List<DrawingDto>> = dao.observe(symbol).map { rows ->
        rows.map { row ->
            val (points, text) = DrawingCodec.decode(json, row.payloadJson)
            DrawingDto(row.id, row.type, points, row.locked, text)
        }
    }

    suspend fun replaceAll(symbol: String, drawings: List<DrawingDto>) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.deleteAll(symbol)
            dao.upsert(
                drawings.mapIndexed { index, drawing ->
                    DrawingEntity(
                        symbol = symbol,
                        id = drawing.id,
                        type = drawing.type,
                        payloadJson = DrawingCodec.encode(json, drawing),
                        locked = drawing.locked,
                        sortOrder = index,
                        updatedAt = now,
                    )
                },
            )
        }
    }
}
