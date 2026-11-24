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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChartDeskDatabase =
        Room.databaseBuilder(context, ChartDeskDatabase::class.java, "chartdesk.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
}
