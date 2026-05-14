package com.gpproject.smartpetitiongenerator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ready_templates")
data class ReadyTemplateEntity(
    // Unique template identifier.
    @PrimaryKey
    val id: String,

    // Template title shown to the user.
    val title: String,

    // Category used for grouping templates.
    val category: String,

    // Full template data stored as JSON.
    val payloadJson: String
)