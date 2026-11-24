package com.chartdesk.app.di

import android.content.Context
import androidx.room.Room
import com.chartdesk.app.data.db.ChartDeskDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChartDeskDatabase =
        Room.databaseBuilder(context, ChartDeskDatabase::class.java, "chartdesk.db")
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideCandleDao(db: ChartDeskDatabase) = db.candleDao()

    @Provides
    fun provideSymbolDao(db: ChartDeskDatabase) = db.symbolDao()
}
