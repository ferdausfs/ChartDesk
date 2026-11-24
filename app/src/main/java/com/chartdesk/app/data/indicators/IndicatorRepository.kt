package com.chartdesk.app.data.indicators

import androidx.room.withTransaction
import com.chartdesk.app.data.db.ChartDeskDatabase
import com.chartdesk.app.data.db.IndicatorDao
import com.chartdesk.app.data.db.IndicatorEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class IndicatorRepository @Inject constructor(
    private val db: ChartDeskDatabase,
    private val dao: IndicatorDao,
    private val json: Json,
) {

    fun observe(symbol: String): Flow<List<IndicatorConfig>> = dao.observe(symbol).map { rows ->
        rows.sortedBy { it.sortOrder }.map { row ->
            IndicatorConfig(
                uid = row.uid,
                type = row.type,
                params = IndicatorCodec.decodeParams(json, row.paramsJson),
                visible = row.visible,
            )
        }
    }

    suspend fun ensureDefault(symbol: String) {
        if (dao.count(symbol) > 0) return
        replace(symbol, IndicatorCatalog.defaultConfigs())
    }

    suspend fun replace(symbol: String, configs: List<IndicatorConfig>) {
        db.withTransaction {
            dao.deleteAll(symbol)
            dao.upsert(
                configs.mapIndexed { index, config ->
                    IndicatorEntity(
                        symbol = symbol,
                        uid = config.uid,
                        type = config.type,
                        paramsJson = IndicatorCodec.encodeParams(json, config.params),
                        visible = config.visible,
                        sortOrder = index,
                    )
                },
            )
        }
    }
}
