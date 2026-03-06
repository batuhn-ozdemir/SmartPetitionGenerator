package com.gpproject.smartpetitiongenerator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Rapor Referansı: Figure 3.1 - PetitionEntity ve Table B.1 DraftHistory [cite: 260, 796]
@Entity(tableName = "draft_history")
data class PetitionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val title: String,            // Dilekçe Başlığı (Örn: "Mazeret Sınavı")
    val finalHtmlContent: String, // Doldurulmuş son HTML kodu
    val createdDate: Long         // Oluşturulma tarihi (Timestamp)
)