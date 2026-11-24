package com.chartdesk.app.di

import com.chartdesk.app.data.market.BinanceProvider
import com.chartdesk.app.data.market.DataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MarketModule {

    @Binds
    @Singleton
    abstract fun bindDataProvider(impl: BinanceProvider): DataProvider
}
