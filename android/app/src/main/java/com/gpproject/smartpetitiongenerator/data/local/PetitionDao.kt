package com.gpproject.smartpetitiongenerator.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PetitionDao {

    // ---------------- USER PROFILE ----------------

    // Saves or updates the user profile.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfile)

    // Returns the saved user profile, if it exists.
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfile(): UserProfile?


    // ---------------- PETITION HISTORY ----------------

    // Inserts a generated petition into the local history.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPetition(petition: PetitionEntity): Long

    // Returns all saved petitions, ordered by newest first.
    @Query("SELECT * FROM draft_history ORDER BY createdDate DESC")
    fun getAllPetitions(): Flow<List<PetitionEntity>>

    // Returns a single petition by its ID.
    @Query("SELECT * FROM draft_history WHERE id = :id")
    suspend fun getPetitionById(id: Int): PetitionEntity?

    // Deletes the selected petition from local history.
    @Delete
    suspend fun deletePetition(petition: PetitionEntity)

    // Updates only the final HTML content of a saved petition.
    @Query("UPDATE draft_history SET finalHtmlContent = :newHtml WHERE id = :id")
    suspend fun updatePetitionHtml(id: Int, newHtml: String)


    // ---------------- READY TEMPLATES ----------------

    // Inserts predefined templates into the local database.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadyTemplates(templates: List<ReadyTemplateEntity>)

    // Removes all predefined templates.
    @Query("DELETE FROM ready_templates")
    suspend fun deleteAllReadyTemplates()

    // Returns all predefined templates.
    @Query("SELECT * FROM ready_templates")
    suspend fun getReadyTemplates(): List<ReadyTemplateEntity>

    // Deletes a predefined template by its exact ID.
    @Query("DELETE FROM ready_templates WHERE id = :templateId")
    suspend fun deleteReadyTemplateById(templateId: String)

    // Deletes predefined templates whose IDs match the given pattern.
    @Query("DELETE FROM ready_templates WHERE id LIKE :prefixPattern")
    suspend fun deleteReadyTemplatesByIdPattern(prefixPattern: String)
}