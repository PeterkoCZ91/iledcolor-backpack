package com.batoh.core.data.di

import com.batoh.core.data.remote.GiphyApi
import com.batoh.core.data.remote.KlipyApi
import com.batoh.core.data.remote.LospecApi
import com.batoh.core.data.repository.*
import com.batoh.core.domain.repository.*
import com.batoh.core.network.di.GiphyRetrofit
import com.batoh.core.network.di.KlipyRetrofit
import com.batoh.core.network.di.LospecRetrofit
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingModule {
    @Binds
    abstract fun bindGiphyRepository(impl: GiphyRepositoryImpl): GiphyRepository

    @Binds
    abstract fun bindKlipyRepository(impl: KlipyRepositoryImpl): KlipyRepository

    @Binds
    abstract fun bindLospecRepository(impl: LospecRepositoryImpl): LospecRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataProviderModule {
    @Provides
    @Singleton
    fun provideGiphyApi(@GiphyRetrofit retrofit: Retrofit): GiphyApi {
        return retrofit.create(GiphyApi::class.java)
    }

    @Provides
    @Singleton
    fun provideKlipyApi(@KlipyRetrofit retrofit: Retrofit): KlipyApi {
        return retrofit.create(KlipyApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLospecApi(@LospecRetrofit retrofit: Retrofit): LospecApi {
        return retrofit.create(LospecApi::class.java)
    }
}
