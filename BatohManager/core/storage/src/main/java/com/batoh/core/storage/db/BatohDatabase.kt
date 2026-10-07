package com.batoh.core.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SearchHistoryEntity::class,
        CachedGifEntity::class,
        CategoryPreferenceEntity::class,
        CachedCategoryEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class BatohDatabase : RoomDatabase() {
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun cachedGifDao(): CachedGifDao
    abstract fun categoryPreferenceDao(): CategoryPreferenceDao
    abstract fun cachedCategoryDao(): CachedCategoryDao

    companion object {
        /** SQL must match [CachedGifEntity] exactly (exportSchema=false, otherwise Room crashes on the device). */
        internal val CACHED_GIFS_V7_SQL: List<String> = listOf(
            "DROP TABLE IF EXISTS `cached_gifs`",
            "CREATE TABLE IF NOT EXISTS `cached_gifs` (" +
                "`id` TEXT NOT NULL, `query` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`thumbnailUrl` TEXT NOT NULL, `originalUrl` TEXT NOT NULL, `mp4Url` TEXT NOT NULL, " +
                "`width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                "`cachedAt` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`query`, `id`))",
            "CREATE INDEX IF NOT EXISTS `index_cached_gifs_query` ON `cached_gifs` (`query`)"
        )

        /** v6→v7: only rebuilds the cache table; category_preferences and history are kept. */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                CACHED_GIFS_V7_SQL.forEach { db.execSQL(it) }
            }
        }

        /** SQL must match [CachedCategoryEntity] exactly. */
        internal val CACHED_CATEGORIES_V8_SQL: List<String> = listOf(
            "CREATE TABLE IF NOT EXISTS `cached_categories` (" +
                "`nameEncoded` TEXT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "`previewId` TEXT NOT NULL, `previewTitle` TEXT NOT NULL, " +
                "`previewThumbnailUrl` TEXT NOT NULL, `previewOriginalUrl` TEXT NOT NULL, " +
                "`previewMp4Url` TEXT NOT NULL, `previewWidth` INTEGER NOT NULL, " +
                "`previewHeight` INTEGER NOT NULL, `previewSource` TEXT NOT NULL, " +
                "`cachedAt` INTEGER NOT NULL, PRIMARY KEY(`nameEncoded`))"
        )

        /** v7→v8: only a new categories table, other tables untouched. */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                CACHED_CATEGORIES_V8_SQL.forEach { db.execSQL(it) }
            }
        }
    }
}
