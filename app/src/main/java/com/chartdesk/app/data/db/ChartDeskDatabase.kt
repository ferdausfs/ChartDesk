package com.chartdesk.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.chartdesk.app.data.Candle
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "candles", primaryKeys = ["symbol", "timeframe", "time"])
data class CandleEntity(
    val symbol: String,
    val timeframe: String,
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
) {
    fun toCandle(): Candle = Candle(time, open, high, low, close, volume)

    companion object {
        fun from(symbol: String, timeframe: String, candle: Candle) = CandleEntity(
            symbol = symbol,
            timeframe = timeframe,
            time = candle.time,
            open = candle.open,
            high = candle.high,
            low = candle.low,
            close = candle.close,
            volume = candle.volume,
        )
    }
}

@Entity(tableName = "symbols")
data class SymbolEntity(
    @PrimaryKey val symbol: String,
    val baseAsset: String,
    val quoteAsset: String,
    val pricePrecision: Int,
    val updatedAt: Long,
)

@Entity(tableName = "watchlists")
data class WatchlistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(tableName = "watchlist_items", primaryKeys = ["listId", "symbol"])
data class WatchlistItemEntity(
    val listId: String,
    val symbol: String,
    val sortOrder: Int,
)

@Dao
interface CandleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<CandleEntity>)

    @Query("SELECT * FROM candles WHERE symbol = :symbol AND timeframe = :timeframe ORDER BY time DESC LIMIT :limit")
    suspend fun latest(symbol: String, timeframe: String, limit: Int): List<CandleEntity>

    @Query("DELETE FROM candles WHERE symbol = :symbol AND timeframe = :timeframe AND time < :cutoff")
    suspend fun prune(symbol: String, timeframe: String, cutoff: Long)
}

@Dao
interface SymbolDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<SymbolEntity>)

    @Query("SELECT * FROM symbols ORDER BY symbol")
    suspend fun all(): List<SymbolEntity>

    @Query("SELECT * FROM symbols WHERE symbol LIKE '%' || :q || '%' OR baseAsset LIKE '%' || :q || '%' ORDER BY symbol LIMIT 40")
    suspend fun search(q: String): List<SymbolEntity>
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlists ORDER BY sortOrder, createdAt")
    fun lists(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlists ORDER BY sortOrder, createdAt")
    suspend fun listsOnce(): List<WatchlistEntity>

    @Query("SELECT * FROM watchlist_items WHERE listId = :listId ORDER BY sortOrder")
    fun items(listId: String): Flow<List<WatchlistItemEntity>>

    @Query("SELECT * FROM watchlist_items WHERE listId = :listId ORDER BY sortOrder")
    suspend fun itemsOnce(listId: String): List<WatchlistItemEntity>

    @Query("SELECT COUNT(*) FROM watchlists")
    suspend fun countLists(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(list: WatchlistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<WatchlistItemEntity>)

    @Query("DELETE FROM watchlist_items WHERE listId = :listId AND symbol = :symbol")
    suspend fun deleteItem(listId: String, symbol: String)

    @Query("DELETE FROM watchlist_items WHERE listId = :listId")
    suspend fun deleteItems(listId: String)

    @Query("DELETE FROM watchlists WHERE id = :listId")
    suspend fun deleteList(listId: String)

    @Query("UPDATE watchlist_items SET sortOrder = :order WHERE listId = :listId AND symbol = :symbol")
    suspend fun setOrder(listId: String, symbol: String, order: Int)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM watchlist_items WHERE listId = :listId")
    suspend fun maxOrder(listId: String): Int
}

@Database(
    entities = [CandleEntity::class, SymbolEntity::class, WatchlistEntity::class, WatchlistItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ChartDeskDatabase : RoomDatabase() {
    abstract fun candleDao(): CandleDao
    abstract fun symbolDao(): SymbolDao
    abstract fun watchlistDao(): WatchlistDao
}
