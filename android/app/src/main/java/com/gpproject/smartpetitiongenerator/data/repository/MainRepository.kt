package com.gpproject.smartpetitiongenerator.data.repository

import android.content.Context
import com.gpproject.smartpetitiongenerator.data.local.PetitionDao
import com.gpproject.smartpetitiongenerator.data.local.PetitionEntity
import com.gpproject.smartpetitiongenerator.data.local.ReadyTemplateEntity
import com.gpproject.smartpetitiongenerator.data.local.TemplateEntity
import com.gpproject.smartpetitiongenerator.data.local.UserProfile
import com.gpproject.smartpetitiongenerator.data.remote.AiApiService
import com.gpproject.smartpetitiongenerator.data.remote.AiResponse
import com.gpproject.smartpetitiongenerator.data.remote.ClientIdProvider
import com.gpproject.smartpetitiongenerator.data.remote.InputField
import com.gpproject.smartpetitiongenerator.data.remote.OcrLayoutRequest
import com.gpproject.smartpetitiongenerator.data.remote.OcrLayoutResponse
import com.gpproject.smartpetitiongenerator.data.remote.OcrQueueResponse
import com.gpproject.smartpetitiongenerator.data.remote.UserPrompt
import com.gpproject.smartpetitiongenerator.domain.ReadyPetitionTemplate
import com.gpproject.smartpetitiongenerator.domain.ReadyPetitionTemplates
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

class MainRepository(
    private val context: Context,
    private val petitionDao: PetitionDao,
    private val apiService: AiApiService
) {
    private val gson = Gson()
    private val templateIconCategories = listOf(
        "Hukuk",
        "Eğitim",
        "Finans",
        "Sağlık",
        "Belediye",
        "İş Hayatı",
        "Genel"
    )

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
                givenParams = payload.givenParams,
                isAiGenerated = entity.id.startsWith("ai_"),
                requiredFields = payload.requiredParams.ifEmpty {
                    extractInputFields(payload.templateHtml)
                }
            )
        }
    }

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

        val initial = runCatching { startAiGeneration(prompt) }.getOrNull() ?: return null
        if (initial.status == "FAILED") return null

        val ticketId = initial.ticketId ?: return normalizeAiCategory(initial.payload)
        repeat(10) {
            delay(1200)
            val status = runCatching { checkAiStatus(ticketId) }.getOrNull() ?: return null
            if (status.status == "FAILED") return null
            if (status.status == "COMPLETED") return normalizeAiCategory(status.payload)
        }
        return null
    }

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

    suspend fun saveUserTemplateAsReadyTemplate(
        title: String,
        templateHtml: String,
        givenParams: Map<String, String> = emptyMap(),
        isAiGenerated: Boolean = true
    ) {
        val requiredFields = extractInputFields(templateHtml)
        val payload = StoredTemplatePayload(
            templateHtml = templateHtml,
            givenParams = givenParams,
            requiredParams = requiredFields
        )
        val prefix = if (isAiGenerated) "ai" else "user"
        val generatedId = "${prefix}_${System.currentTimeMillis()}"
        val category = if (isAiGenerated) {
            inferTemplateCategoryByAi(title, templateHtml) ?: inferTemplateCategory(title, templateHtml)
        } else {
            inferTemplateCategory(title, templateHtml)
        }

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

    suspend fun deleteAiGeneratedReadyTemplate(templateId: String) {
        if (!templateId.startsWith("ai_")) return
        petitionDao.deleteReadyTemplateById(templateId)
    }

    private suspend fun syncBuiltInReadyTemplates() {

        val seedRows = ReadyPetitionTemplates.templates.map { template ->
            val payload = StoredTemplatePayload(
                templateHtml = template.templateHtml,
                givenParams = emptyMap(),
                // Built-in hazır şablonlarda hangi alanların sorulacağını
                // template.requiredFields belirler; HTML placeholder'larından
                // otomatik türetme burada alanları gereksiz daraltabiliyor.
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

    private fun extractInputFields(templateHtml: String): List<InputField> {
        val regex = Regex("\\{\\{\\s*([A-Z0-9_]+)\\s*\\}\\}")
        val orderedKeys = linkedSetOf<String>()
        regex.findAll(templateHtml).forEach { match ->
            orderedKeys += match.groupValues[1].trim().uppercase()
        }

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
            .filterNot { it == "BUGUN_TARIH" || it == "EKLER_BOLUMU" }
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

    // --- REMOTE ---
    suspend fun startAiGeneration(promptText: String): AiResponse {
        return apiService.sendPrompt(clientId, UserPrompt(promptText))
    }

    suspend fun checkAiStatus(ticketId: String): AiResponse {
        return apiService.checkStatus(clientId, ticketId)
    }

    suspend fun enqueueOcrAnalysis(imageBase64: String, mimeType: String): OcrQueueResponse {
        return apiService.enqueueOcrLayout(
            clientId = clientId,
            request = OcrLayoutRequest(
                imageBase64 = imageBase64,
                mimeType = mimeType
            )
        )
    }

    suspend fun checkOcrStatus(ticketId: String): OcrQueueResponse {
        return apiService.checkOcrStatus(
            clientId = clientId,
            ticketId = ticketId
        )
    }
}

data class StoredTemplatePayload(
    val templateHtml: String,
    val givenParams: Map<String, String> = emptyMap(),
    val requiredParams: List<InputField> = emptyList()
)
