package com.gpproject.smartpetitiongenerator.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 1,

    // Full name of the user.
    val fullName: String,

    // National identity number of the user.
    val identityNumber: String,

    // Contact phone number.
    val phoneNumber: String,

    // Address used in generated petitions.
    val address: String,
)