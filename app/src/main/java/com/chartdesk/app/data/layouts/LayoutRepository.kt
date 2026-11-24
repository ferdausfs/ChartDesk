package com.chartdesk.app.data.layouts

import com.chartdesk.app.data.db.LayoutDaoDb
import com.chartdesk.app.data.db.LayoutEntityDb
import com.chartdesk.app.data.ChartStateBus
import com.chartdesk.app.data.ChartSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class SavedLayout(
    val id: String,
    val name: String,
    val symbol: String,
    val timeframe: String,
    val chartType: String,
    val grid: Int,
    val createdAt: Long,
)

@Singleton
class LayoutRepository @Inject constructor(
    private val dao: LayoutDaoDb,
    private val stateBus: ChartStateBus,
) {
    val layouts: Flow<List<SavedLayout>> = dao.observeAll().map { rows ->
        rows.map { SavedLayout(it.id, it.name, it.symbol, it.timeframe, it.chartType, it.grid, it.createdAt) }
    }

    suspend fun save(name: String): String {
        val current: ChartSummary = stateBus.state.first()
        val id = "layout-" + System.currentTimeMillis()
        dao.upsert(
            LayoutEntityDb(
                id = id,
                name = name.trim().ifEmpty { "Layout" },
                symbol = current.symbol,
                timeframe = current.timeframe,
                chartType = current.chartType,
                grid = current.grid,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun delete(id: String) = dao.delete(id)
}
