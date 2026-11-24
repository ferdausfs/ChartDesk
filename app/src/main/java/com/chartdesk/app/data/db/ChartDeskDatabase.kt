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

@Entity(tableName = "layouts")
data class LayoutEntityDb(
    @PrimaryKey val id: String,
    val name: String,
    val symbol: String,
    val timeframe: String,
    val chartType: String,
    val grid: Int,
    val createdAt: Long,
)

@Dao
interface LayoutDaoDb {
    @Query("SELECT * FROM layouts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LayoutEntityDb>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(layout: LayoutEntityDb)

    @Query("DELETE FROM layouts WHERE id = :id")
    suspend fun delete(id: String)
}

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val direction: String,
    val price: Double,
    val active: Boolean = true,
    val createdAt: Long,
    val triggeredAt: Long? = null,
    val remoteId: String? = null,
)

@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey val id: String,
    val alertId: String,
    val symbol: String,
    val direction: String,
    val target: Double,
    val price: Double,
    val triggeredAt: Long,
    val seen: Boolean = false,
)

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE active = 1")
    suspend fun activeOnce(): List<AlertEntity>

    @Query("SELECT * FROM alert_events ORDER BY triggeredAt DESC LIMIT 50")
    fun observeEvents(): Flow<List<AlertEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alert: AlertEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AlertEventEntity)

    @Query("UPDATE alerts SET active = 0, triggeredAt = :at WHERE id = :id")
    suspend fun markTriggered(id: String, at: Long)

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE alert_events SET seen = 1 WHERE seen = 0")
    suspend fun markEventsSeen()
}

@Entity(tableName = "scripts")
data class ScriptEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val updatedAt: Long,
    val builtIn: Boolean = false,
)

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts ORDER BY updatedAt DESC")
    suspend fun allOnce(): List<ScriptEntity>

    @Query("SELECT COUNT(*) FROM scripts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(script: ScriptEntity)

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun delete(id: String)
}

@Entity(tableName = "drawings", primaryKeys = ["symbol", "id"])
data class DrawingEntity(
    val symbol: String,
    val id: String,
    val type: String,
    val payloadJson: String,
    val locked: Boolean,
    val sortOrder: Int,
    val updatedAt: Long,
)

@Dao
interface DrawingDao {
    @Query("SELECT * FROM drawings WHERE symbol = :symbol ORDER BY sortOrder")
    fun observe(symbol: String): Flow<List<DrawingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<DrawingEntity>)

    @Query("DELETE FROM drawings WHERE symbol = :symbol")
    suspend fun deleteAll(symbol: String)
}

@Entity(tableName = "indicators", primaryKeys = ["symbol", "uid"])
data class IndicatorEntity(
    val symbol: String,
    val uid: String,
    val type: String,
    val paramsJson: String,
    val visible: Boolean,
    val sortOrder: Int,
)

@Dao
interface IndicatorDao {
    @Query("SELECT * FROM indicators WHERE symbol = :symbol ORDER BY sortOrder")
    fun observe(symbol: String): Flow<List<IndicatorEntity>>

    @Query("SELECT COUNT(*) FROM indicators WHERE symbol = :symbol")
    suspend fun count(symbol: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<IndicatorEntity>)

    @Query("DELETE FROM indicators WHERE symbol = :symbol")
    suspend fun deleteAll(symbol: String)
}

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
    entities = [
        CandleEntity::class,
        SymbolEntity::class,
        WatchlistEntity::class,
        WatchlistItemEntity::class,
        IndicatorEntity::class,
        DrawingEntity::class,
        ScriptEntity::class,
        AlertEntity::class,
        AlertEventEntity::class,
        LayoutEntityDb::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class ChartDeskDatabase : RoomDatabase() {
    abstract fun candleDao(): CandleDao
    abstract fun symbolDao(): SymbolDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun indicatorDao(): IndicatorDao
    abstract fun drawingDao(): DrawingDao
    abstract fun scriptDao(): ScriptDao
    abstract fun alertDao(): AlertDao
    abstract fun layoutDao(): LayoutDaoDb
}
