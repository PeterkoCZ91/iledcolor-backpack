package com.batoh.core.storage.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_preferences")
data class CategoryPreferenceEntity(
    @PrimaryKey val query: String,
    val pinned: Boolean = false,
    val lastUsedAt: Long = 0L
)

