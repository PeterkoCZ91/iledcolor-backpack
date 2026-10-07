package com.batoh.core.storage.di

import android.content.Context
import androidx.room.Room
import com.batoh.core.domain.repository.CategoryPreferencesRepository
import com.batoh.core.domain.repository.LocalMediaRepository
import com.batoh.core.storage.db.BatohDatabase
import com.batoh.core.storage.db.CategoryPreferenceDao
import com.batoh.core.storage.repository.CategoryPreferencesRepositoryImpl
import com.batoh.core.storage.repository.LocalMediaRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageBindingModule {
    @Binds
    abstract fun bindLocalMediaRepository(
        impl: LocalMediaRepositoryImpl
    ): LocalMediaRepository

    @Binds
    abstract fun bindSearchHistoryRepository(
        impl: com.batoh.core.storage.repository.SearchHistoryRepositoryImpl
    ): com.batoh.core.domain.repository.SearchHistoryRepository

    @Binds
    abstract fun bindCategoryPreferencesRepository(
        impl: CategoryPreferencesRepositoryImpl
    ): CategoryPreferencesRepository
}

@Module
@InstallIn(SingletonComponent::class)
object StorageProviderModule {
    @Provides
    @Singleton
    fun provideBatohDatabase(@ApplicationContext context: Context): BatohDatabase {
        return Room.databaseBuilder(
            context,
            BatohDatabase::class.java,
            "batoh_database"
        )
        .addMigrations(BatohDatabase.MIGRATION_6_7, BatohDatabase.MIGRATION_7_8)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
    }

    @Provides
    fun provideSearchHistoryDao(database: BatohDatabase): com.batoh.core.storage.db.SearchHistoryDao {
        return database.searchHistoryDao()
    }

    @Provides
    fun provideCachedGifDao(database: BatohDatabase): com.batoh.core.storage.db.CachedGifDao {
        return database.cachedGifDao()
    }

    @Provides
    fun provideCategoryPreferenceDao(database: BatohDatabase): CategoryPreferenceDao {
        return database.categoryPreferenceDao()
    }

    @Provides
    fun provideCachedCategoryDao(database: BatohDatabase): com.batoh.core.storage.db.CachedCategoryDao {
        return database.cachedCategoryDao()
    }
}
