package com.batoh.core.storage.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatohMigrationTest {
    private val createSql get() = BatohDatabase.CACHED_GIFS_V7_SQL[1]

    @Test
    fun migrationVersionsAndOnlyTouchesCachedGifs() {
        val m = BatohDatabase.MIGRATION_6_7
        assertEquals(6, m.startVersion)
        assertEquals(7, m.endVersion)
        val executed = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") executed += args[0] as String
            null
        } as SupportSQLiteDatabase
        m.migrate(db)
        assertEquals(BatohDatabase.CACHED_GIFS_V7_SQL, executed)
        executed.forEach {
            assertTrue(it, it.contains("`cached_gifs`"))
            assertTrue(it, !it.contains("category_preferences") && !it.contains("search_history"))
        }
    }

    @Test
    fun createSqlMatchesEntity() {
        val cols = Regex("`(\\w+)` (TEXT|INTEGER) NOT NULL")
            .findAll(createSql.substringBefore(", PRIMARY KEY"))
            .associate { it.groupValues[1] to it.groupValues[2] }
        val fields = CachedGifEntity::class.java.declaredFields.filter { !it.isSynthetic && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(fields.map { it.name }.toSet(), cols.keys)
        fields.forEach {
            val expected = when (it.type) {
                String::class.java -> "TEXT"
                Int::class.javaPrimitiveType, Long::class.javaPrimitiveType -> "INTEGER"
                else -> error("neočekávaný typ ${it.name}")
            }
            assertEquals(it.name, expected, cols[it.name])
        }
        assertTrue(createSql.contains("PRIMARY KEY(`query`, `id`)"))
        assertEquals("CREATE INDEX IF NOT EXISTS `index_cached_gifs_query` ON `cached_gifs` (`query`)", BatohDatabase.CACHED_GIFS_V7_SQL[2])
    }
}

class BatohMigration7To8Test {
    private val createSql get() = BatohDatabase.CACHED_CATEGORIES_V8_SQL.single()

    @Test
    fun migrationVersionsAndOnlyCreatesNewTable() {
        val m = BatohDatabase.MIGRATION_7_8
        assertEquals(7, m.startVersion)
        assertEquals(8, m.endVersion)
        val executed = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") executed += args[0] as String
            null
        } as SupportSQLiteDatabase
        m.migrate(db)
        assertEquals(BatohDatabase.CACHED_CATEGORIES_V8_SQL, executed)
        executed.forEach {
            assertTrue(it, it.startsWith("CREATE TABLE IF NOT EXISTS `cached_categories`"))
            assertTrue(it, !it.contains("DROP") && !it.contains("cached_gifs") &&
                !it.contains("category_preferences") && !it.contains("search_history"))
        }
    }

    @Test
    fun createSqlMatchesEntity() {
        val cols = Regex("`(\\w+)` (TEXT|INTEGER) NOT NULL")
            .findAll(createSql.substringBefore(", PRIMARY KEY"))
            .associate { it.groupValues[1] to it.groupValues[2] }
        val fields = CachedCategoryEntity::class.java.declaredFields
            .filter { !it.isSynthetic && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(fields.map { it.name }.toSet(), cols.keys)
        fields.forEach {
            val expected = when (it.type) {
                String::class.java -> "TEXT"
                Int::class.javaPrimitiveType, Long::class.javaPrimitiveType -> "INTEGER"
                else -> error("neočekávaný typ ${it.name}")
            }
            assertEquals(it.name, expected, cols[it.name])
        }
        // Pořadí sloupců = pořadí polí entity.
        assertEquals(fields.map { it.name }, Regex("`(\\w+)` (?:TEXT|INTEGER) NOT NULL")
            .findAll(createSql).map { it.groupValues[1] }.toList())
        assertTrue(createSql.endsWith("PRIMARY KEY(`nameEncoded`))"))
        assertTrue(!createSql.contains("INDEX"))
        // Room anotace nejsou za běhu; PK/indexy hlídá zdroj entity.
        val src = java.io.File("src/main/java/com/batoh/core/storage/db/CachedCategoryEntity.kt").readText()
        assertTrue(src.contains("tableName = \"cached_categories\""))
        assertTrue(src.contains("primaryKeys = [\"nameEncoded\"]"))
        assertTrue(!src.contains("indices"))
    }

    @Test
    fun databaseVersionIs8AndListsEntity() {
        val src = java.io.File("src/main/java/com/batoh/core/storage/db/BatohDatabase.kt").readText()
        assertTrue(src.contains("version = 8"))
        assertTrue(src.contains("CachedCategoryEntity::class"))
    }
}
