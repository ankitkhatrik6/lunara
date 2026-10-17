/**
 * Ankit Khatri KC (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.lunara.app.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "set_video_id")
data class SetVideoIdEntity(
    @PrimaryKey(autoGenerate = false)
    val videoId: String = "",
    val setVideoId: String? = null,
)
