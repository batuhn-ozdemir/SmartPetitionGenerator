package com.gpproject.smartpetitiongenerator.data.repository

import android.content.Context
import com.gpproject.smartpetitiongenerator.data.local.PetitionDao
import com.gpproject.smartpetitiongenerator.data.local.PetitionEntity
import com.gpproject.smartpetitiongenerator.data.local.ReadyTemplateEntity
import com.gpproject.smartpetitiongenerator.data.local.UserProfile
import com.gpproject.smartpetitiongenerator.data.remote.AiApiService
import com.gpproject.smartpetitiongenerator.data.remote.AiResponse
import com.gpproject.smartpetitiongenerator.data.remote.ClientIdProvider
import com.gpproject.smartpetitiongenerator.data.remote.InputField
import com.gpproject.smartpetitiongenerator.data.remote.OcrLayoutRequest
import com.gpproject.smartpetitiongenerator.data.remote.OcrQueueResponse
import com.gpproject.smartpetitiongenerator.data.remote.UserPrompt
import com.gpproject.smartpetitiongenerator.data.seed.ReadyPetitionTemplate
import com.gpproject.smartpetitiongenerator.data.seed.ReadyPetitionTemplates
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

class MainRepository(
    private val context: Context,
    private val petitionDao: PetitionDao,
    private val apiService: AiApiService
) {
    private val gson = Gson()

    // Available template categories used for icon/group matching.
    private val templateIconCategories = listOf(
        "Hukuk",
        "Eğitim",
        "Finans",
        "Sağlık",
        "Belediye",
        "İş Hayatı",
        "Genel"
    )

    // Provides a persistent client ID for backend requests.
    private val clientId: String by lazy { ClientIdProvider.getClientId(context) }


    // ---------------- LOCAL DATABASE OPERATIONS ----------------

    // Returns the saved user profile, if it exists.
    suspend fun getUserProfile(): UserProfile? = petitionDao.getUserProfile()

    // Saves or updates the user profile.
    suspend fun saveUserProfile(profile: UserProfile) = petitionDao.saveUserProfile(profile)

    // Provides all saved petition drafts as a reactive Flow.
    val allPetitions: Flow<List<PetitionEntity>> = petitionDao.getAllPetitions()

    // Saves a generated petition draft into local history.
    suspend fun savePetitionDraft(petition: PetitionEntity): Long =
        petitionDao.insertPetition(petition)

    // Deletes a petition from local history.
    suspend fun deletePetition(petition: PetitionEntity) = petitionDao.deletePetition(petition)

    // Returns a single petition by its local database ID.
    suspend fun getPetitionById(id: Int): PetitionEntity? = petitionDao.getPetitionById(id)

    // Updates only the HTML content of an existing petition.
    suspend fun updatePetition(id: Int, html: String) = petitionDao.updatePetitionHtml(id, html)


    // ---------------- READY TEMPLATE OPERATIONS ----------------

    // Loads built-in, user-saved, and AI-generated ready templates.
    suspend fun getReadyTemplates(): List<ReadyPetitionTemplate> {
        syncBuiltInReadyTemplates()

        return petitionDao.getReadyTemplates().mapNotNull { entity ->
            // Parse stored JSON payload safely. Invalid records are skipped.
            val payload = runCatching {
                gson.fromJson(entity.payloadJson, StoredTemplatePayload::class.java)
            }.getOrNull() ?: return@mapNotNull null

            // Convert database entity into the domain model used by the app.
            ReadyPetitionTemplate(
                id = entity.id,
                title = entity.title,
                category = entity.category,
                templateHtml = payload.templateHtml,
                givenParams = payload.givenParams,
                isAiGenerated = entity.id.startsWith("ai_"),
                requiredFields = payload.requiredParams.ifEmpty {
                    extractInputFields(payload.templateHtml)
                }
            )
        }
    }

    // Infers a template category by checking keywords in the title and HTML.
    private fun inferTemplateCategory(title: String, templateHtml: String): String {
        val probe = "$title $templateHtml".lowercase()

        val categoryKeywords = listOf(
            "Hukuk" to listOf("hukuk", "mahkeme", "dava", "ceza", "itiraz", "savcılık", "hakimlik", "suç", "tutanak", "tebligat"),
            "Eğitim" to listOf("eğitim", "egitim", "üniversite", "universite", "öğrenci", "ogrenci", "sınav", "sinav", "okul", "fakülte", "fakulte"),
            "Finans" to listOf("banka", "finans", "komisyon", "ücret", "ucret", "iade", "kredi", "hesap", "borç", "borc", "fatura", "ödeme", "odeme"),
            "Belediye" to listOf("belediye", "altyapı", "altyapi", "yol", "imar", "çevre", "cevre", "apartman", "site yönetimi", "gürültü", "gurultu"),
            "İş Hayatı" to listOf("iş", "is", "çalış", "calis", "izin", "personel", "maaş", "maas", "işveren", "isveren", "mesai", "sgk", "tazminat"),
            "Sağlık" to listOf("sağlık", "saglik", "hastane", "doktor", "rapor", "tedavi", "ilaç", "ilac", "randevu", "muayene")
        )

        val best = categoryKeywords
            .map { (category, keywords) ->
                category to keywords.count { keyword -> keyword in probe }
            }
            .maxByOrNull { it.second }

        return if (best != null && best.second > 0) {
            best.first
        } else {
            "Genel"
        }
    }

    // Uses AI to choose the closest category for a generated template.
    private suspend fun inferTemplateCategoryByAi(title: String, templateHtml: String): String? {
        val prompt = buildString {
            appendLine("Görevin: Bir dilekçe şablonunu mevcut uygulama ikon kategorilerinden EN YAKIN olana eşlemek.")
            appendLine("SADECE aşağıdaki kategorilerden birini döndür:")
            appendLine(templateIconCategories.joinToString(", "))
            appendLine()
            appendLine("Kurallar:")
            appendLine("- Tek satır döndür.")
            appendLine("- Yorum, açıklama, markdown, noktalama ekleme.")
            appendLine("- Emin değilsen 'Genel' döndür.")
            appendLine()
            appendLine("Şablon başlığı: $title")
            appendLine("Şablon metni:")
            appendLine(templateHtml.take(4000))
        }

        // Start AI category detection and return null if the request fails.
        val initial = runCatching { startAiGeneration(prompt) }.getOrNull() ?: return null
        if (initial.status == "FAILED") return null

        // If the backend returns the result directly, normalize the payload.
        val ticketId = initial.ticketId ?: return normalizeAiCategory(initial.payload)

        // Poll the backend for a limited time until the AI result is completed.
        repeat(10) {
            delay(1200)

            val status = runCatching { checkAiStatus(ticketId) }.getOrNull() ?: return null

            if (status.status == "FAILED") return null
            if (status.status == "COMPLETED") return normalizeAiCategory(status.payload)
        }

        return null
    }

    // Cleans and validates the category returned by AI.
    private fun normalizeAiCategory(rawPayload: String?): String? {
        val payload = rawPayload?.trim().orEmpty()
        if (payload.isBlank()) return null

        val normalized = payload
            .replace("```", "")
            .replace("\"", "")
            .replace("'", "")
            .trim()

        val exact = templateIconCategories.firstOrNull { category ->
            normalized.equals(category, ignoreCase = true)
        }
        if (exact != null) return exact

        return templateIconCategories.firstOrNull { category ->
            normalized.contains(category, ignoreCase = true)
        }
    }

    // Saves an AI-generated or user-created template into the ready_templates table.
    suspend fun saveUserTemplateAsReadyTemplate(
        title: String,
        templateHtml: String,
        givenParams: Map<String, String> = emptyMap(),
        isAiGenerated: Boolean = true
    ) {
        // Extract input fields from {{PLACEHOLDER}} values in the template HTML.
        val requiredFields = extractInputFields(templateHtml)
            .filterNot { isAiGenerated && it.key == "EKLER_LISTESI" }

        // Store the template details as a JSON payload.
        val payload = StoredTemplatePayload(
            templateHtml = templateHtml,
            givenParams = if (isAiGenerated) {
                givenParams.filterKeys { it.uppercase() != "EKLER_LISTESI" }
            } else {
                givenParams
            },
            requiredParams = requiredFields
        )

        // Prefix is used later to understand whether the template was generated by AI.
        val prefix = if (isAiGenerated) "ai" else "user"
        val generatedId = "${prefix}_${System.currentTimeMillis()}"

        // AI-generated templates first try AI category detection, then fallback to keyword matching.
        val category = if (isAiGenerated) {
            inferTemplateCategoryByAi(title, templateHtml) ?: inferTemplateCategory(title, templateHtml)
        } else {
            inferTemplateCategory(title, templateHtml)
        }

        // Save the template into ready_templates as a ReadyTemplateEntity.
        petitionDao.insertReadyTemplates(
            listOf(
                ReadyTemplateEntity(
                    id = generatedId,
                    title = title,
                    category = category,
                    payloadJson = gson.toJson(payload)
                )
            )
        )
    }

    // Deletes only AI-generated ready templates.
    suspend fun deleteAiGeneratedReadyTemplate(templateId: String) {
        if (!templateId.startsWith("ai_")) return
        petitionDao.deleteReadyTemplateById(templateId)
    }

    // Synchronizes built-in ready templates into the local database.
    private suspend fun syncBuiltInReadyTemplates() {
        val seedRows = ReadyPetitionTemplates.templates.map { template ->
            val payload = StoredTemplatePayload(
                templateHtml = template.templateHtml,
                givenParams = emptyMap(),

                // Built-in templates use predefined required fields.
                // This avoids extracting too many fields from HTML placeholders.
                requiredParams = template.requiredFields
            )

            ReadyTemplateEntity(
                id = template.id,
                title = template.title,
                category = template.category,
                payloadJson = gson.toJson(payload)
            )
        }

        petitionDao.insertReadyTemplates(seedRows)
    }

    // Extracts input fields from placeholders such as {{AD_SOYAD}} or {{SINAV_TARIHI}}.
    private fun extractInputFields(templateHtml: String): List<InputField> {
        val regex = Regex("\\{\\{\\s*([A-Z0-9_]+)\\s*\\}\\}")
        val orderedKeys = linkedSetOf<String>()

        // Keep placeholder order and avoid duplicate fields.
        regex.findAll(templateHtml).forEach { match ->
            orderedKeys += match.groupValues[1].trim().uppercase()
        }

        // Converts common technical keys into user-friendly labels.
        val staticLabels = mapOf(
            "AD_SOYAD" to "Ad Soyad",
            "TCKN" to "T.C. Kimlik No",
            "TELEFON" to "Telefon",
            "ADRES" to "Adres",
            "MAKAMIN_ADI" to "Başvurulan Makam",
            "KONU_KISA_OZETI" to "Konu Özeti",
            "DOSYA_NO" to "Dosya / Referans No",
            "BASVURU_TARIHI" to "Başvuru Tarihi",
            "OLAY_TARIHI" to "Olay / İşlem Tarihi",
            "TEBLIGAT_TARIHI" to "Tebligat Tarihi",
            "DELIL_VE_BELGELER" to "Delil ve Belgeler",
            "EKLER_LISTESI" to "Ekler"
        )

        return orderedKeys
            // These fields are handled automatically or separately.
            .filterNot {
                it == "BUGUN_TARIH" ||
                        it == "EKLER_BOLUMU" ||
                        it == "EKLER_LISTESI" ||
                        it == "EK_VAR_MI"
            }
            .map { key ->
                InputField(
                    key = key,
                    label = staticLabels[key] ?: key.split("_").joinToString(" ") { part ->
                        part.lowercase().replaceFirstChar { c -> c.titlecase() }
                    },
                    type = when (key) {
                        "TCKN", "OGRENCI_NO" -> "number"
                        "TELEFON" -> "phone"
                        else -> if ("TARIH" in key) "date" else "text"
                    }
                )
            }
    }


    // ---------------- REMOTE API OPERATIONS ----------------

    // Sends a prompt to the backend for AI petition generation.
    suspend fun startAiGeneration(promptText: String): AiResponse {
        return apiService.sendPrompt(clientId, UserPrompt(promptText))
    }

    // Checks the status of an AI generation request by ticket ID.
    suspend fun checkAiStatus(ticketId: String): AiResponse {
        return apiService.checkStatus(clientId, ticketId)
    }

    // Sends an image to the backend for OCR layout analysis.
    suspend fun enqueueOcrAnalysis(imageBase64: String, mimeType: String): OcrQueueResponse {
        return apiService.enqueueOcrLayout(
            clientId = clientId,
            request = OcrLayoutRequest(
                imageBase64 = imageBase64,
                mimeType = mimeType
            )
        )
    }

    // Checks the status of an OCR layout analysis request by ticket ID.
    suspend fun checkOcrStatus(ticketId: String): OcrQueueResponse {
        return apiService.checkOcrStatus(
            clientId = clientId,
            ticketId = ticketId
        )
    }
}

// Payload stored as JSON inside ReadyTemplateEntity.payloadJson.
data class StoredTemplatePayload(
    val templateHtml: String,
    val givenParams: Map<String, String> = emptyMap(),
    val requiredParams: List<InputField> = emptyList()
)