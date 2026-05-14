package com.gpproject.smartpetitiongenerator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draft_history")
data class PetitionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // Display title of the generated petition.
    val title: String,

    // Final filled HTML content of the petition.
    val finalHtmlContent: String,

    // Creation time stored as timestamp.
    val createdDate: Long
)