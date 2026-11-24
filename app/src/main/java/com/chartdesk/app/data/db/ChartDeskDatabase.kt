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

@Database(entities = [CandleEntity::class, SymbolEntity::class], version = 1, exportSchema = false)
abstract class ChartDeskDatabase : RoomDatabase() {
    abstract fun candleDao(): CandleDao
    abstract fun symbolDao(): SymbolDao
}
