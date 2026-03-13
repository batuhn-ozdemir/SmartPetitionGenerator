package com.gpproject.smartpetitiongenerator.data.repository

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

class MainRepository(
    private val context: Context,
    private val petitionDao: PetitionDao,
    private val apiService: AiApiService
) {
    private val gson = Gson()

    // client id tek yerde
    private val clientId: String by lazy { ClientIdProvider.getClientId(context) }

    // --- LOCAL ---
    suspend fun getUserProfile(): UserProfile? = petitionDao.getUserProfile()

    suspend fun saveUserProfile(profile: UserProfile) = petitionDao.saveUserProfile(profile)

    val allPetitions: Flow<List<PetitionEntity>> = petitionDao.getAllPetitions()

    suspend fun savePetitionDraft(petition: PetitionEntity): Long =
        petitionDao.insertPetition(petition)

    suspend fun deletePetition(petition: PetitionEntity) = petitionDao.deletePetition(petition)

    suspend fun getPetitionById(id: Int): PetitionEntity? = petitionDao.getPetitionById(id)

    suspend fun saveTemplate(template: TemplateEntity) = petitionDao.insertTemplate(template)

    suspend fun updatePetition(id: Int, html: String) = petitionDao.updatePetitionHtml(id, html)

    suspend fun getReadyTemplates(): List<ReadyPetitionTemplate> {
        syncBuiltInReadyTemplates()
        return petitionDao.getReadyTemplates().mapNotNull { entity ->
            val payload = runCatching {
                gson.fromJson(entity.payloadJson, StoredTemplatePayload::class.java)
            }.getOrNull() ?: return@mapNotNull null

            ReadyPetitionTemplate(
                id = entity.id,
                title = entity.title,
                category = entity.category,
                templateHtml = payload.templateHtml,
                requiredFields = payload.requiredParams.ifEmpty {
                    extractInputFields(payload.templateHtml)
                }
            )
        }
    }
}