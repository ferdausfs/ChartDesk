package com.chartdesk.app.data.watchlist

import androidx.room.withTransaction
import com.chartdesk.app.data.db.ChartDeskDatabase
import com.chartdesk.app.data.db.WatchlistDao
import com.chartdesk.app.data.db.WatchlistEntity
import com.chartdesk.app.data.db.WatchlistItemEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Watchlist(
    val id: String,
    val name: String,
)

data class WatchlistItem(
    val symbol: String,
    val sortOrder: Int,
)

@Singleton
class WatchlistRepository @Inject constructor(
    private val db: ChartDeskDatabase,
    private val dao: WatchlistDao,
) {

    val lists: Flow<List<Watchlist>> = dao.lists().map { rows ->
        rows.map { Watchlist(it.id, it.name) }
    }

    fun items(listId: String): Flow<List<WatchlistItem>> = dao.items(listId).map { rows ->
        rows.map { WatchlistItem(it.symbol, it.sortOrder) }
    }

    suspend fun ensureDefault() {
        if (dao.countLists() > 0) return
        val now = System.currentTimeMillis()
        val listId = "main"
        dao.upsertList(WatchlistEntity(listId, "Main", 0, now))
        dao.upsertItems(
            DEFAULT_SYMBOLS.mapIndexed { index, symbol ->
                WatchlistItemEntity(listId, symbol, index)
            },
        )
    }

    suspend fun createList(name: String): String {
        val lists = dao.listsOnce()
        val id = UUID.randomUUID().toString()
        val order = (lists.maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.upsertList(WatchlistEntity(id, name.trim().ifEmpty { "List" }, order, System.currentTimeMillis()))
        return id
    }

    suspend fun renameList(listId: String, name: String) {
        val current = dao.listsOnce().firstOrNull { it.id == listId } ?: return
        dao.upsertList(current.copy(name = name.trim().ifEmpty { current.name }))
    }

    suspend fun deleteList(listId: String) {
        db.withTransaction {
            dao.deleteItems(listId)
            dao.deleteList(listId)
        }
    }

    suspend fun addSymbol(listId: String, symbol: String) {
        val clean = symbol.trim().uppercase()
        if (clean.isEmpty()) return
        val next = dao.maxOrder(listId) + 1
        dao.upsertItems(listOf(WatchlistItemEntity(listId, clean, next)))
    }

    suspend fun removeSymbol(listId: String, symbol: String) {
        db.withTransaction {
            dao.deleteItem(listId, symbol)
            renumber(listId)
        }
    }

    suspend fun move(listId: String, from: Int, to: Int) {
        db.withTransaction {
            val symbols = dao.itemsOnce(listId).map { it.symbol }
            val reordered = reorder(symbols, from, to)
            reordered.forEachIndexed { index, symbol -> dao.setOrder(listId, symbol, index) }
        }
    }

    private suspend fun renumber(listId: String) {
        dao.itemsOnce(listId).forEachIndexed { index, item -> dao.setOrder(listId, item.symbol, index) }
    }

    companion object {
        val DEFAULT_SYMBOLS = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT")

        /** Pure reorder used by the UI and by tests. Out-of-range moves return the original order. */
        fun reorder(symbols: List<String>, from: Int, to: Int): List<String> {
            if (from == to) return symbols
            if (from !in symbols.indices || to !in symbols.indices) return symbols
            val mutable = symbols.toMutableList()
            val moved = mutable.removeAt(from)
            mutable.add(to, moved)
            return mutable
        }
    }
}
