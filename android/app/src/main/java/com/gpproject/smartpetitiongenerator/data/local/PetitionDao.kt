package com.gpproject.smartpetitiongenerator.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PetitionDao {

    // --- PROFİL ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfile)

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfile(): UserProfile?

    // --- DİLEKÇE GEÇMİŞİ ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPetition(petition: PetitionEntity): Long

    @Query("SELECT * FROM draft_history ORDER BY createdDate DESC")
    fun getAllPetitions(): Flow<List<PetitionEntity>>

    @Query("SELECT * FROM draft_history WHERE id = :id")
    suspend fun getPetitionById(id: Int): PetitionEntity?

    @Delete
    suspend fun deletePetition(petition: PetitionEntity)

    @Query("UPDATE draft_history SET finalHtmlContent = :newHtml WHERE id = :id")
    suspend fun updatePetitionHtml(id: Int, newHtml: String)

    // --- TEMPLATE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TemplateEntity)

    @Query("SELECT * FROM templates WHERE isUserGenerated = 1")
    fun getUserTemplates(): Flow<List<TemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadyTemplates(templates: List<ReadyTemplateEntity>)

    @Query("DELETE FROM ready_templates")
    suspend fun deleteAllReadyTemplates()

    @Query("SELECT * FROM ready_templates")
    suspend fun getReadyTemplates(): List<ReadyTemplateEntity>
}