package com.gpproject.smartpetitiongenerator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ready_templates")
data class ReadyTemplateEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val category: String,
    val payloadJson: String
)
