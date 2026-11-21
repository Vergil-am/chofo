package dev.dogetime.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One app preference, such as the selected theme, stored as text under its [key]. */
@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
)
