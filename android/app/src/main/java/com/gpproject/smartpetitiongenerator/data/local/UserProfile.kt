package com.gpproject.smartpetitiongenerator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 1,

    val fullName: String,       // Ad Soyad
    val identityNumber: String, // T.C. Kimlik No
    val phoneNumber: String,    // Telefon
    val address: String,        // Adres
)