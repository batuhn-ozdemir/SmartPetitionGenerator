package com.gpproject.smartpetitiongenerator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,
    val rawJsonStructure: String,
    val requiredParams: List<String>,
    val isUserGenerated: Boolean = false
)