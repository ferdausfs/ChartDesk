package com.chartdesk.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chartdesk.app.data.db.ChartDeskDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `watchlists` (" +
                "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `watchlist_items` (" +
                "`listId` TEXT NOT NULL, `symbol` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "PRIMARY KEY(`listId`, `symbol`))",
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `indicators` (" +
                "`symbol` TEXT NOT NULL, `uid` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`paramsJson` TEXT NOT NULL, `visible` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                "PRIMARY KEY(`symbol`, `uid`))",
        )
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `drawings` (" +
                "`symbol` TEXT NOT NULL, `id` TEXT NOT NULL, `type` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, " +
                "`locked` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`symbol`, `id`))",
        )
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `scripts` (" +
                "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `code` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `alerts` (" +
                "`id` TEXT NOT NULL, `symbol` TEXT NOT NULL, `direction` TEXT NOT NULL, `price` REAL NOT NULL, " +
                "`active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `triggeredAt` INTEGER, `remoteId` TEXT, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `alert_events` (" +
                "`id` TEXT NOT NULL, `alertId` TEXT NOT NULL, `symbol` TEXT NOT NULL, `direction` TEXT NOT NULL, " +
                "`target` REAL NOT NULL, `price` REAL NOT NULL, `triggeredAt` INTEGER NOT NULL, `seen` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChartDeskDatabase =
        Room.databaseBuilder(context, ChartDeskDatabase::class.java, "chartdesk.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideCandleDao(db: ChartDeskDatabase) = db.candleDao()

    @Provides
    fun provideSymbolDao(db: ChartDeskDatabase) = db.symbolDao()

    @Provides
    fun provideWatchlistDao(db: ChartDeskDatabase) = db.watchlistDao()

    @Provides
    fun provideIndicatorDao(db: ChartDeskDatabase) = db.indicatorDao()

    @Provides
    fun provideDrawingDao(db: ChartDeskDatabase) = db.drawingDao()

    @Provides
    fun provideScriptDao(db: ChartDeskDatabase) = db.scriptDao()

    @Provides
    fun provideAlertDao(db: ChartDeskDatabase) = db.alertDao()
}
