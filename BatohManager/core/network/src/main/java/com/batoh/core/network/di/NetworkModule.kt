package com.batoh.core.network.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GiphyRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KlipyRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LospecRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .apply {
                if (com.batoh.core.network.BuildConfig.DEBUG) {
                    // Request lines only, with key-like query parameters masked: debug builds are
                    // also published by CI, and users enter their own API keys.
                    addInterceptor(okhttp3.logging.HttpLoggingInterceptor { message ->
                        android.util.Log.d("OkHttp", maskApiKeys(message))
                    }.apply {
                        level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()
    }

    @Provides
    @Singleton
    @GiphyRetrofit
    fun provideGiphyRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.giphy.com/v1/gifs/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    @KlipyRetrofit
    fun provideKlipyRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.klipy.com/v2/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    @LospecRetrofit
    fun provideLospecRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://lospec.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}
