package com.gpproject.smartpetitiongenerator.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gpproject.smartpetitiongenerator.data.local.PetitionEntity
import com.gpproject.smartpetitiongenerator.data.local.UserProfile
import com.gpproject.smartpetitiongenerator.data.remote.AiGeneratedPetition
import com.gpproject.smartpetitiongenerator.data.remote.AiResponse
import com.gpproject.smartpetitiongenerator.data.remote.InputField
import com.gpproject.smartpetitiongenerator.data.remote.InputFieldDeserializer
import com.gpproject.smartpetitiongenerator.data.repository.MainRepository
import com.gpproject.smartpetitiongenerator.domain.TemplateEngine
import com.gpproject.smartpetitiongenerator.domain.ReadyPetitionTemplate
import com.google.gson.GsonBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException
import kotlin.math.min

class PetitionViewModel(private val repository: MainRepository) : ViewModel() {

    enum class PreviewOrigin {
        NONE,
        AI_ASSISTANT,
        READY_TEMPLATE,
        OCR,
        MANUAL
    }

    val historyList: StateFlow<List<PetitionEntity>> = repository.allPetitions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _aiState = mutableStateOf<AiState>(AiState.Idle)
    val aiState: State<AiState> = _aiState

    private val _userProfile = mutableStateOf<UserProfile?>(null)
    val userProfile: State<UserProfile?> = _userProfile

    private val gson = GsonBuilder()
        .registerTypeAdapter(InputField::class.java, InputFieldDeserializer())
        .create()

    private var lastUserPrompt: String = ""
    private var lastResolvedParams: Map<String, String> = emptyMap()
    private var lastTemplateSourceHtml: String? = null
    private val _currentPreviewHtml = mutableStateOf<String?>(null)
    val currentPreviewHtml: State<String?> = _currentPreviewHtml
    private val _canSaveCurrentPreviewAsTemplate = mutableStateOf(false)
    val canSaveCurrentPreviewAsTemplate: State<Boolean> = _canSaveCurrentPreviewAsTemplate
    private val _currentPreviewOrigin = mutableStateOf(PreviewOrigin.NONE)
    val currentPreviewOrigin: State<PreviewOrigin> = _currentPreviewOrigin

    private val _readyTemplates = mutableStateOf<List<ReadyPetitionTemplate>>(emptyList())
    val readyTemplates: State<List<ReadyPetitionTemplate>> = _readyTemplates

    private enum class DraftMode { AI, READY_TEMPLATE }
    private var draftMode: DraftMode = DraftMode.AI


    private val personalOrder = listOf("AD_SOYAD", "TCKN", "TELEFON", "ADRES")
    private val maxTemplateChars = 30000
    private val maxFinalHtmlChars = 60000

    init {
        loadProfile()
        loadReadyTemplates()
    }

    private fun loadReadyTemplates() {
        viewModelScope.launch {
            _readyTemplates.value = repository.getReadyTemplates()
        }
    }

    fun loadProfile() {
        viewModelScope.launch { _userProfile.value = repository.getUserProfile() }
    }

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.saveUserProfile(profile)
            _userProfile.value = profile
        }
    }

    private suspend fun pollUntilCompleted(ticketId: String): AiResponse {
        while (true) {
            delay(2000)
            val res = runCatching { repository.checkAiStatus(ticketId) }.getOrNull() ?: continue
            if (res.status == "COMPLETED" && res.payload != null) return res
            if (res.status == "FAILED") return res
        }
    }

    // ---------- Dinamik alanları template placeholderlarından çıkar ----------
    private fun extractPlaceholders(html: String): Set<String> {
        val regex = Regex("""\{\{\s*([A-Za-z0-9_]+)\s*\}\}""")
        return regex.findAll(html)
            .map { canonicalKey(it.groupValues[1]) }
            .toSet()
    }

    private fun labelForKey(key: String): String = when (key) {
        "AD_SOYAD" -> "Ad Soyad"
        "TCKN" -> "T.C. Kimlik No"
        "TELEFON" -> "Telefon"
        "ADRES" -> "Adres"
        "KURUM_ADI" -> "Kurum Adı (Örn: İstanbul Üniversitesi)"
        "MAKAMIN_ADI" -> "Makam Adı (Örn: Fakülte Dekanlığı'na)"
        "KONU_KISA_OZETI" -> "Konu (Kısa Özet)"
        "MAZERET_DETAYI" -> "Gerekçe / Açıklama"
        "SINAV_TARIHI" -> "Tarih"
        "OGRENCI_NO" -> "Öğrenci No"
        "CEZA_NO" -> "Ceza / Karar No"
        "TEBLIGAT_TARIHI" -> "Tebligat Tarihi"
        "EK_VAR_MI" -> "Ek var mı? (Evet / Hayır)"
        "ISLEM_NO" -> "İşlem / Dosya No"
        "OLAY_TARIHI" -> "Olay / İşlem Tarihi"
        "BASVURU_SEBEBI" -> "Başvuru Sebebi"
        "OLAY_ACIKLAMASI" -> "Olayın Detaylı Açıklaması"
        "HUKUKI_DAYANAK" -> "Hukuki / İdari Dayanak"
        "TALEP_METNI" -> "Açık Talep Metni"
        "MAGDURIYET_ETKISI" -> "Yaşanan Mağduriyet / Etki"
        "TESLIM_TERCIHI" -> "Tebligat / Cevap Tercihi"
        "DOSYA_NO" -> "Dosya / Referans No"
        "BASVURU_TARIHI" -> "Başvuru Tarihi"
        "DELIL_VE_BELGELER" -> "Delil ve Belgeler"
        "EKLER_LISTESI" -> "Ekler (varsa her satıra bir ek)"
        else -> humanLabelFromKey(key)
    }

    private fun typeForKey(key: String): String = when (key) {
        "TCKN", "OGRENCI_NO" -> "number"
        "TELEFON" -> "phone"
        "SINAV_TARIHI", "TEBLIGAT_TARIHI", "OLAY_TARIHI", "BASVURU_TARIHI", "ISLEM_TARIHI", "IZIN_BASLANGIC", "IZIN_BITIS" -> "date"
        else -> "text"
    }

    private fun buildAttachmentSection(params: Map<String, String>): String {
        val wantsAttachments = params["EK_VAR_MI"].orEmpty().trim().equals("evet", ignoreCase = true)
        if (!wantsAttachments) return ""

        val attachments = params["EKLER_LISTESI"].orEmpty().trim()
        if (attachments.isBlank()) return ""

        return """
            <div class="contact-footer">
                <b>Ekler:</b><br/>
                $attachments
            </div>
        """.trimIndent()
    }

    private fun canonicalKey(raw: String): String {
        var k = raw.trim().uppercase()
        while (k.startsWith("_")) k = k.substring(1)
        while (k.endsWith("_")) k = k.substring(0, k.length - 1)
        return k.replace(" ", "_")
    }

    private fun humanLabelFromKey(key: String): String {
        return key
            .split("_")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    private fun profileParams(): Map<String, String> {
        val p = _userProfile.value ?: return emptyMap()
        val m = mutableMapOf<String, String>()
        if (p.fullName.isNotBlank()) m["AD_SOYAD"] = p.fullName
        if (p.identityNumber.isNotBlank()) m["TCKN"] = p.identityNumber
        if (p.phoneNumber.isNotBlank()) m["TELEFON"] = p.phoneNumber
        if (p.address.isNotBlank()) m["ADRES"] = p.address
        return m
    }

    /** Formda göstereceğimiz alan listesi:
     *  - template placeholder keys
     *  - givenParams keys (prompttan yakalananlar) -> kullanıcı görsün/düzeltsin
     *  - kişisel alanlar (4 tane) -> profilde varsa dolu görünsün
     */
    private fun buildFormFields(
        templateHtml: String,
        givenParams: Map<String, String>,
        requiredParams: List<InputField>,
        includeTemplatePlaceholders: Boolean = true
    ): List<InputField> {
        val keyToField = linkedMapOf<String, InputField>()

        fun upsert(rawKey: String, label: String? = null, type: String? = null) {
            val key = canonicalKey(rawKey)
            if (key.isBlank()) return
            val prev = keyToField[key]
            keyToField[key] = InputField(
                key = key,
                label = label?.takeIf { it.isNotBlank() } ?: prev?.label ?: labelForKey(key),
                type = type?.takeIf { it.isNotBlank() } ?: prev?.type ?: typeForKey(key)
            )
        }

        if (includeTemplatePlaceholders) extractPlaceholders(templateHtml).forEach { upsert(it) }
        givenParams.keys.forEach { upsert(it) }
        requiredParams.forEach { upsert(it.key, it.label, it.type) }

        personalOrder.forEach { upsert(it) }

        val rest = keyToField.keys.filterNot { it in personalOrder }.sorted()
        val ordered = personalOrder + rest

        return ordered.mapNotNull { keyToField[it] }
    }

    fun startReadyTemplateFlow(template: ReadyPetitionTemplate) {
        draftMode = DraftMode.READY_TEMPLATE
        _canSaveCurrentPreviewAsTemplate.value = false
        lastUserPrompt = template.title
        lastTemplateSourceHtml = template.templateHtml

        val given = (template.givenParams + mapOf(
            "BUGUN_TARIH" to java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale("tr", "TR")).format(java.util.Date())
        )).mapValues { it.value.trim() }

        val fieldsForForm = buildFormFields(
            templateHtml = template.templateHtml,
            givenParams = given,
            requiredParams = template.requiredFields,
            includeTemplatePlaceholders = false
        )
        val data = AiGeneratedPetition(
            templateHtml = template.templateHtml,
            givenParams = given,
            requiredParams = fieldsForForm
        )
        _aiState.value = AiState.NeedsInput(data)
    }

    private fun extractBackendErrorMessage(templateHtml: String): String? {
        val regex = Regex("<p>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return regex.find(templateHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("<[^>]*>"), "")
            ?.replace("&lt;", "<")
            ?.replace("&gt;", ">")
            ?.replace("&amp;", "&")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun getReadyTemplateById(id: String?): ReadyPetitionTemplate? {
        if (id.isNullOrBlank()) return null
        return readyTemplates.value.firstOrNull { it.id == id }
    }

    // -------------------- 1) DRAFT (JSON) --------------------
    fun generatePetition(prompt: String) {
        viewModelScope.launch {
            try {
                _aiState.value = AiState.Loading
                draftMode = DraftMode.AI
                lastUserPrompt = prompt

                val initial = repository.startAiGeneration(prompt)
                if (initial.status == "FAILED") {
                    _aiState.value = AiState.Error(initial.payload ?: "Sunucu şu anda yoğun. Lütfen tekrar deneyin.")
                    return@launch
                }
                val ticketId = initial.ticketId ?: run {
                    _aiState.value = AiState.Error(initial.payload ?: "Bilet alınamadı.")
                    return@launch
                }

                val done = pollUntilCompleted(ticketId)

                if (done.status == "FAILED") {
                    val payload = done.payload.orEmpty()
                    val isSuspicious = payload.contains("şüpheli işlem", ignoreCase = true)
                    val isOutOfScope = payload.contains("görev dışı", ignoreCase = true)
                    val isRateLimited = payload.contains("quota", ignoreCase = true) ||
                            payload.contains("429") ||
                            payload.contains("rate limit", ignoreCase = true) ||
                            payload.contains("resource exhausted", ignoreCase = true) ||
                            payload.contains("exceeded", ignoreCase = true)
                    _aiState.value = when {
                        isSuspicious ->
                            AiState.Error("Şüpheli işlem algılandı. Lütfen talebinizi resmi dilekçe formatında yeniden giriniz.")
                        isOutOfScope ->
                            AiState.Error("Görev dışı işlem: Bu servis yalnızca dilekçe oluşturma taleplerini işler.")
                        isRateLimited ->
                            AiState.Error("İstek limiti doldu. Lütfen 30-60 saniye bekleyip tekrar deneyin.")
                        else ->
                            AiState.Error("İşlem şu anda tamamlanamadı. Lütfen tekrar deneyiniz.")
                    }
                    return@launch
                }

                val payload = done.payload!!.trim()
                if (!payload.startsWith("{")) {
                    _aiState.value = AiState.Error("Sunucu beklenen JSON yerine farklı bir çıktı döndürdü.")
                    return@launch
                }

                val parsed = gson.fromJson(payload, AiGeneratedPetition::class.java)

                val template = parsed.templateHtml?.trim()
                if (template.isNullOrBlank()) {
                    _aiState.value = AiState.Error("Yapay zeka geçerli bir şablon üretemedi. Lütfen tekrar deneyin.")
                    return@launch
                }
                if (template.length > maxTemplateChars) {
                    _aiState.value = AiState.Error("Üretilen taslak çok uzun geldi. Lütfen daha kısa ve net bir talep girin.")
                    return@launch
                }

                if (template.contains("HATA", ignoreCase = true)) {
                    val backendMessage = extractBackendErrorMessage(template).orEmpty()
                    val safeUiMessage = if (backendMessage.contains("güvenlik", ignoreCase = true) ||
                        backendMessage.contains("şüpheli", ignoreCase = true)) {
                        "Şüpheli işlem algılandı. Lütfen talebinizi resmi dilekçe formatında yeniden giriniz."
                    } else if (backendMessage.contains("görev dışı", ignoreCase = true)) {
                        "Görev dışı işlem: Bu servis yalnızca dilekçe oluşturma taleplerini işler."
                    } else if (backendMessage.contains("quota", ignoreCase = true) ||
                        backendMessage.contains("429") ||
                        backendMessage.contains("rate limit", ignoreCase = true) ||
                        backendMessage.contains("resource exhausted", ignoreCase = true) ||
                        backendMessage.contains("exceeded", ignoreCase = true)) {
                        "İstek limiti doldu. Lütfen 30-60 saniye bekleyip tekrar deneyin."
                    } else {
                        "İşlem şu anda tamamlanamadı. Lütfen tekrar deneyiniz."
                    }
                    _aiState.value = AiState.Error(safeUiMessage)
                    return@launch
                }

                val given = parsed.givenParams ?: emptyMap()
                val required = parsed.requiredParams ?: emptyList()

                // template + given + required + kişisel -> form alanları
                val fieldsForForm = buildFormFields(template, given, required)

                val normalized = AiGeneratedPetition(
                    templateHtml = template,
                    givenParams = given,
                    requiredParams = fieldsForForm
                )

                _aiState.value = AiState.NeedsInput(normalized)

            } catch (e: Exception) {
                _aiState.value = AiState.Error("Hata: ${e.localizedMessage}")
            }
        }
    }

    // -------------------- 2) FINAL (Form + Profil -> AI’a geri gönder) --------------------
    fun submitDynamicForm(petitionData: AiGeneratedPetition, userInputs: Map<String, String>) {
        viewModelScope.launch {
            try {
                _aiState.value = AiState.Loading

                val template = petitionData.templateHtml?.trim().orEmpty()
                if (template.isBlank()) {
                    _aiState.value = AiState.Error("Şablon boş geldi. Tekrar deneyin.")
                    return@launch
                }
                lastTemplateSourceHtml = template

                val given = petitionData.givenParams ?: emptyMap()
                val prof = profileParams()

                // Profil, AI'ın buldukları ve kullanıcının formda girdiklerini birleştiriyoruz.
                // Eğer kullanıcı boş bıraktıysa, value boşluk ("") olarak kalacak.
                val allParams = (prof + given + userInputs).mapValues { it.value.trim() } +
                        ("EKLER_BOLUMU" to buildAttachmentSection(prof + given + userInputs))
                lastResolvedParams = allParams

                // =========================================================================
                // DİKKAT: Eskiden burada olan "Eksik alanlar var (missing.isNotEmpty)"
                // kontrolünü TAMAMEN SİLDİK.
                // Artık kullanıcı her yeri boş bıraksa bile istek sunucuya (AI'a) gidecek
                // ve yapay zeka "Smart Defaults" kuralı ile o boşlukları mantıklı şekilde uyduracak.
                // =========================================================================

                val fullHtml = if (draftMode == DraftMode.READY_TEMPLATE) {

                    val rendered = renderTemplate(template, allParams)
                    if (rendered.isBlank()) {
                        _aiState.value = AiState.Error("Şablon oluşturulamadı. Tekrar deneyin.")
                        return@launch
                    }
                    TemplateEngine.wrapContentInA4(rendered)
                } else {
                    val finalPrompt = buildFinalPrompt(lastUserPrompt, template, allParams)

                    val initial = repository.startAiGeneration(finalPrompt)
                    val ticketId = initial.ticketId ?: run {
                        _aiState.value = AiState.Error("Bilet alınamadı.")
                        return@launch
                    }

                    val done = pollUntilCompleted(ticketId) ?: run {
                        _aiState.value = AiState.Error("Zaman aşımı. Tekrar deneyin.")
                        return@launch
                    }

                    if (done.status == "FAILED") {
                        _aiState.value = AiState.Error("İşlem başarısız oldu.")
                        return@launch
                    }

                    val finalHtml = done.payload.orEmpty()
                        .replace("```html", "")
                        .replace("```", "")
                        .trim()

                    if (finalHtml.isBlank()) {
                        _aiState.value = AiState.Error("Final dilekçe boş geldi. Tekrar deneyin.")
                        return@launch
                    }
                    if (finalHtml.length > maxFinalHtmlChars) {
                        _aiState.value = AiState.Error("Yapay zeka çıktısı çok uzun geldi. Lütfen talebi daha kısa girin.")
                        return@launch
                    }

                    TemplateEngine.wrapContentInA4(finalHtml)
                }

                _currentPreviewHtml.value = fullHtml
                _canSaveCurrentPreviewAsTemplate.value = draftMode == DraftMode.AI
                _currentPreviewOrigin.value = if (draftMode == DraftMode.AI) {
                    PreviewOrigin.AI_ASSISTANT
                } else {
                    PreviewOrigin.READY_TEMPLATE
                }
                _aiState.value = AiState.Success

            } catch (e: Exception) {
                _aiState.value = AiState.Error("Hata: ${e.localizedMessage}")
            }
        }
    }

    private fun renderTemplate(templateHtml: String, params: Map<String, String>): String {
        val safeTemplate = normalizeBrokenPlaceholders(templateHtml)
        val regex = Regex("""\{\{\s*([A-Za-z0-9_]+)\s*\}\}""")

        return regex.replace(safeTemplate) { match ->
            val key = canonicalKey(match.groupValues[1])
            val value = params[key].orEmpty().trim()

            when {
                key == "EKLER_BOLUMU" || key == "EKLER_LISTESI" || key == "EK_VAR_MI" -> ""
                value.isNotBlank() -> value
                else -> match.value
            }
        }
    }

    private fun buildFinalPrompt(originalPrompt: String, templateHtml: String, params: Map<String, String>): String {
        val sb = StringBuilder()
        val missingKeys = extractPlaceholders(templateHtml)
            .filterNot { key -> params[key]?.isNotBlank() == true }
            .sorted()

        sb.append("FINAL_BUILD:\n")
        sb.append("ORIGINAL_REQUEST: ").append(originalPrompt).append("\n\n")

        sb.append("TEMPLATE_HTML:\n")
        sb.append(templateHtml).append("\n\n")

        sb.append("PARAMS:\n")
        params.forEach { (k, v) ->
            if (v.isNotBlank()) sb.append(k).append(": ").append(v).append("\n")
        }

        if (missingKeys.isNotEmpty()) {
            sb.append("\nMISSING_KEYS:\n")
            missingKeys.forEach { sb.append("- ").append(it).append("\n") }
        }

        sb.append(
            """

        INSTRUCTIONS (STRICT):
        - Output MUST be HTML only. No JSON. No markdown. No explanations.
        - Keep TEMPLATE_HTML structure and ALL class names EXACTLY as provided.
        - Replace every {{KEY}} placeholder using PARAMS values.
        - If a key is listed under MISSING_KEYS, infer the most suitable formal default from context and fill it (do not leave placeholders).
        - Keep the petition concise: include only essential facts.
        - Do NOT invent extra date ranges or timeline details (start/end). Use only provided data/context.
        - Never fabricate personal identifiers (TCKN, phone, address, student no).
        - If the template already contains a label like "<b>Konu:</b>", then KONU_KISA_OZETI must NOT contain "Konu:" or "Subject:".
        - Avoid duplication: do not repeat the same meaning/phrase in the subject line or first paragraph.
        - Capitalization:
            * Institution/recipient headers may be ALL CAPS, but preserve Turkish letters correctly (İ/Ş/Ğ/Ü/Ö/Ç).
            * Paragraph text must be normal sentence case (no Title Case inside sentences).
        - Before returning HTML, silently self-check and fix:
            1) Subject line appears only once and is not duplicated.
            2) No unnecessary repeated phrases.
            3) No incorrect capitalization inside paragraphs.

        """
        )
        return sb.toString()
    }

    fun resetState() {
        _aiState.value = AiState.Idle
        draftMode = DraftMode.AI
        lastUserPrompt = ""
    }

    fun clearCurrentPreview() {
        _currentPreviewHtml.value = null
        _canSaveCurrentPreviewAsTemplate.value = false
        _currentPreviewOrigin.value = PreviewOrigin.NONE
        lastResolvedParams = emptyMap()
        lastTemplateSourceHtml = null
    }

    fun updateCurrentPreviewHtml(newHtml: String) {
        _currentPreviewHtml.value = newHtml
        _canSaveCurrentPreviewAsTemplate.value = false
        _currentPreviewOrigin.value = PreviewOrigin.MANUAL
    }

    fun createPreviewFromOcrTextLayout(
        imageWidthPx: Int,
        imageHeightPx: Int,
        lines: List<OcrPositionedText>
    ) {
        if (imageWidthPx <= 0 || imageHeightPx <= 0 || lines.isEmpty()) {
            _aiState.value = AiState.Error("Fotoğraftan okunabilir metin bulunamadı.")
            return
        }

        val renderableLines = buildRenderableLines(
            imageWidthPx = imageWidthPx,
            imageHeightPx = imageHeightPx,
            lines = lines
        )

        val rawHtml = buildFlowingOcrHtml(imageWidthPx, imageHeightPx, renderableLines)

        _currentPreviewHtml.value = TemplateEngine.wrapContentInA4(rawHtml)
        _canSaveCurrentPreviewAsTemplate.value = false
        _currentPreviewOrigin.value = PreviewOrigin.OCR
        _aiState.value = AiState.Success
    }

    suspend fun createPreviewFromGeminiDocumentLayout(
        imageBytes: ByteArray,
        mimeType: String
    ): GeminiOcrPreviewResult {
        if (imageBytes.isEmpty()) {
            return GeminiOcrPreviewResult.Error("Fotoğraf verisi boş.")
        }

        val encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val response = runCatching {
            repository.analyzeDocumentLayout(
                imageBase64 = encoded,
                mimeType = mimeType
            )
        }.getOrElse {
            val message = when (it) {
                is SocketTimeoutException ->
                    "Belge analizi zaman aşımına uğradı. Lütfen birkaç saniye bekleyip tekrar deneyin."
                is UnknownHostException ->
                    "Sunucuya erişilemedi. İnternet bağlantınızı ve backend adresinizi kontrol edin."
                is IOException ->
                    "Sunucu bağlantısında geçici bir sorun oluştu. Lütfen tekrar deneyin."
                else ->
                    "Belge analizi şu an tamamlanamadı. Lütfen tekrar deneyin."
            }
            return GeminiOcrPreviewResult.Error(message)
        }

        if (!response.success) {
            val message = when (response.errorCode?.uppercase()) {
                "LOW_QUALITY" -> "Fotoğraf kalitesi düşük veya yazılar okunamıyor. Lütfen daha net bir fotoğraf yükleyin."
                "NOT_DOCUMENT" -> "Yüklenen görselde okunabilir bir kağıt tespit edilemedi."
                "NOT_PETITION" -> "Bu belge dilekçe formatına uygun görünmüyor."
                "RATE_LIMITED" -> "OCR limiti doldu. Lütfen 30-60 saniye sonra tekrar deneyin."
                "PAYLOAD_TOO_LARGE" -> "Fotoğraf boyutu çok büyük. Daha düşük çözünürlüklü bir görsel yükleyin."
                else -> response.errorMessage ?: "Belge analizi tamamlanamadı."
            }
            val normalizedMessage = when {
                message.contains("exceeded", ignoreCase = true) ||
                        message.contains("resource exhausted", ignoreCase = true) ||
                        message.contains("quota", ignoreCase = true) ||
                        message.contains("429") -> "OCR limiti doldu. Lütfen 30-60 saniye sonra tekrar deneyin."
                message.contains("payload", ignoreCase = true) ||
                        message.contains("too large", ignoreCase = true) -> "Fotoğraf boyutu çok büyük. Daha düşük çözünürlüklü bir görsel yükleyin."
                else -> message
            }
            return GeminiOcrPreviewResult.Error(normalizedMessage)
        }

        if (!response.petition) {
            return GeminiOcrPreviewResult.Error("Belge dilekçe olarak tespit edilemedi.")
        }

        if (response.confidence < 0.68) {
            return GeminiOcrPreviewResult.Error("Fotoğraf kalitesi düşük bulundu (güven: ${"%.2f".format(java.util.Locale.US, response.confidence)}).")
        }

        val lines = response.lines
            .filter { it.text.isNotBlank() }
            .map {
                OcrPositionedText(
                    text = it.text.trim(),
                    leftPx = it.leftPx,
                    topPx = it.topPx,
                    widthPx = it.widthPx,
                    heightPx = it.heightPx
                )
            }

        if (lines.isEmpty()) {
            return GeminiOcrPreviewResult.Error("Metin satırları çıkarılamadı. Lütfen daha net bir fotoğraf deneyin.")
        }

        createPreviewFromOcrTextLayout(
            imageWidthPx = response.imageWidth,
            imageHeightPx = response.imageHeight,
            lines = lines
        )
        return GeminiOcrPreviewResult.Success(response.confidence)
    }

    fun createPreviewFromReadyTemplate(template: ReadyPetitionTemplate, extractedInputs: Map<String, String>) {
        val given = (template.givenParams + mapOf(
            "BUGUN_TARIH" to java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale("tr", "TR")).format(java.util.Date())
        )).mapValues { it.value.trim() }
        val prof = profileParams()
        val allParams = (prof + given + extractedInputs).mapValues { it.value.trim() } +
                ("EKLER_BOLUMU" to buildAttachmentSection(prof + given + extractedInputs))
        lastResolvedParams = allParams

        val rendered = renderTemplate(template.templateHtml, allParams)
        if (rendered.isBlank()) {
            _aiState.value = AiState.Error("Şablon oluşturulamadı. Lütfen tekrar deneyin.")
            return
        }

        _currentPreviewHtml.value = TemplateEngine.wrapContentInA4(rendered)
        _canSaveCurrentPreviewAsTemplate.value = false
        _currentPreviewOrigin.value = PreviewOrigin.READY_TEMPLATE
        _aiState.value = AiState.Success
    }


    suspend fun saveCurrentPreviewToHistory(title: String): Int? {
        val html = _currentPreviewHtml.value?.trim().orEmpty()
        if (html.isBlank()) return null

        val requestedTitle = title.trim().ifBlank {
            "Dilekçe - ${System.currentTimeMillis()}"
        }

        val existingTitles = historyList.value.map { it.title }.toSet()
        val normalizedTitle = if (requestedTitle !in existingTitles) {
            requestedTitle
        } else {
            var suffix = 1
            var candidate = "$requestedTitle $suffix"
            while (candidate in existingTitles) {
                suffix += 1
                candidate = "$requestedTitle $suffix"
            }
            candidate
        }

        val newDraft = PetitionEntity(
            title = normalizedTitle,
            finalHtmlContent = html,
            createdDate = System.currentTimeMillis()
        )
        return repository.savePetitionDraft(newDraft).toInt()
    }

    fun saveCurrentPreviewToHistory(title: String, onSaved: (Int?) -> Unit) {
        viewModelScope.launch {
            onSaved(saveCurrentPreviewToHistory(title))
        }
    }

    suspend fun getPetitionById(id: Int) = repository.getPetitionById(id)

    fun saveEditedPetition(id: Int, newHtml: String) {
        viewModelScope.launch { repository.updatePetition(id, newHtml) }
    }

    // ✅ AI ile üretilen taslağı, ready_templates formatında DB'ye göm
    fun saveAsTemplate(templateName: String, aiData: AiGeneratedPetition) {
        viewModelScope.launch {
            val template = aiData.templateHtml?.trim()
            if (template.isNullOrBlank()) {
                _aiState.value = AiState.Error("Şablon kaydedilemedi: şablon boş.")
                return@launch
            }

            repository.saveUserTemplateAsReadyTemplate(
                title = templateName,
                templateHtml = template,
                givenParams = aiData.givenParams ?: emptyMap(),
                isAiGenerated = true
            )

            loadReadyTemplates()
        }
    }

    private fun extractA4InnerHtml(wrappedHtml: String): String {
        val wrapperRegex = Regex(
            """<div\s+class=["']a4-page["'][^>]*>([\s\S]*)</div>\s*</body>""",
            setOf(RegexOption.IGNORE_CASE)
        )
        return wrapperRegex.find(wrappedHtml)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
    }

    private fun normalizeBrokenPlaceholders(html: String): String {
        return html.replace(
            Regex("""\{\{\{\s*([A-Za-z0-9_]+)\s*\}\}\}"""),
            "{{$1}}"
        )
    }

    private fun sanitizePreviewHtmlToTemplate(innerHtml: String): String {

        if (innerHtml.isBlank()) return ""

        var sanitized = normalizeBrokenPlaceholders(innerHtml)

        val knownKeys = lastResolvedParams.keys
            .map { canonicalKey(it) }
            .toSet()

        // Sadece gerçekten placeholder gibi duran tek süslü alanları dönüştür
        sanitized = sanitized.replace(
            Regex("""(?<!\{)\{\s*([A-Za-z0-9_]+)\s*\}(?!\})""")
        ) { match ->
            val key = canonicalKey(match.groupValues[1])
            if (key in knownKeys) "{{${key}}}" else match.value
        }

        val templateCandidates = lastResolvedParams
            .filterKeys { it != "BUGUN_TARIH" && it != "EKLER_BOLUMU" }
            .filterValues { it.isNotBlank() }
            .toList()
            .sortedByDescending { (_, value) -> value.length }

        templateCandidates.forEach { (key, value) ->
            val placeholder = "{{${canonicalKey(key)}}}"
            if (!sanitized.contains(placeholder)) {
                sanitized = sanitized.replace(value, placeholder)
            }
        }

        return sanitized.trim()
    }

    fun saveCurrentPreviewAsTemplate(
        templateName: String,
        currentPreviewHtml: String?,
        preferTemplateSource: Boolean,
        onSaved: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val wrappedHtml = currentPreviewHtml?.trim().orEmpty()
            if (wrappedHtml.isBlank()) {
                onSaved(false)
                return@launch
            }

            val normalizedTemplate = if (preferTemplateSource) {
                val source = lastTemplateSourceHtml?.trim().orEmpty()
                if (source.isNotBlank()) sanitizePreviewHtmlToTemplate(source) else ""
            } else {
                ""
            }.ifBlank {
                val innerHtml = extractA4InnerHtml(wrappedHtml)
                sanitizePreviewHtmlToTemplate(innerHtml)
            }

            if (normalizedTemplate.isBlank()) {
                onSaved(false)
                return@launch
            }

            repository.saveUserTemplateAsReadyTemplate(
                title = templateName.trim().ifBlank { "Özel Şablon" },
                templateHtml = normalizedTemplate,
                givenParams = emptyMap(),
                isAiGenerated = (draftMode == DraftMode.AI)
            )
            loadReadyTemplates()
            onSaved(true)
        }
    }

    fun deleteAiTemplate(templateId: String) {
        viewModelScope.launch {
            repository.deleteAiGeneratedReadyTemplate(templateId)
            loadReadyTemplates()
        }
    }

    fun deletePetition(p: PetitionEntity) =
        viewModelScope.launch { repository.deletePetition(p) }
}

private data class OcrRenderableLine(
    val text: String,
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val fontPx: Int
)

private fun buildRenderableLines(
    imageWidthPx: Int,
    imageHeightPx: Int,
    lines: List<OcrPositionedText>
): List<OcrRenderableLine> {
    val sorted = lines.sortedWith(compareBy<OcrPositionedText> { it.topPx }.thenBy { it.leftPx })
    val heights = sorted.map { it.heightPx.coerceAtLeast(1) }.sorted()
    val medianHeight = heights[heights.size / 2].coerceIn(11, 24)
    val baseFontPx = (medianHeight * 0.62f).toInt().coerceIn(11, 15)

    val deduped = mutableListOf<OcrPositionedText>()
    sorted.forEach { candidate ->
        val textKey = normalizeOcrText(candidate.text)
        val duplicated = deduped.any { existing ->
            normalizeOcrText(existing.text) == textKey &&
                    kotlin.math.abs(existing.leftPx - candidate.leftPx) <= 4 &&
                    kotlin.math.abs(existing.topPx - candidate.topPx) <= 4
        }
        if (!duplicated) deduped += candidate
    }

    return deduped.map { line ->
        val widthPx = line.widthPx.coerceIn(1, imageWidthPx)
        val leftPx = line.leftPx.coerceIn(0, imageWidthPx - 1)
        val topPx = line.topPx.coerceIn(0, imageHeightPx - 1)
        val fontPx = (line.heightPx * 0.58f).toInt().coerceIn(baseFontPx - 1, baseFontPx + 2)

        OcrRenderableLine(
            text = line.text,
            leftPx = leftPx,
            topPx = topPx,
            widthPx = min(widthPx, (imageWidthPx - leftPx).coerceAtLeast(1)),
            heightPx = line.heightPx.coerceAtLeast(1),
            fontPx = fontPx
        )
    }
}

private fun normalizeOcrText(text: String): String {
    return text.trim().lowercase().replace(Regex("\\s+"), " ")
}

private fun isLikelyOfficialHeaderLine(text: String): Boolean {
    val normalized = text
        .trim()
        .replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return false

    val uppercaseRatio = normalized.count { it.isLetter() && it.isUpperCase() }
        .toFloat() / normalized.count { it.isLetter() }.coerceAtLeast(1)

    val hasHeaderKeyword = listOf(
        "T.C.", "TC ", "SAYIN", "KONU", "İLGİ", "BAŞKANLIĞINA",
        "MÜDÜRLÜĞÜNE", "MAKAMINA", "VALİLİĞİNE", "KAYMAKAMLIĞINA"
    ).any { normalized.uppercase().contains(it) }

    return hasHeaderKeyword || uppercaseRatio >= 0.90f
}

private fun buildFlowingOcrHtml(
    imageWidthPx: Int,
    imageHeightPx: Int,
    lines: List<OcrRenderableLine>
): String {
    if (lines.isEmpty()) return "<p><br/></p>"

    val sorted = lines.sortedWith(compareBy<OcrRenderableLine> { it.topPx }.thenBy { it.leftPx })
    val medianHeight = sorted.map { it.heightPx.coerceAtLeast(1) }.sorted().let { it[it.size / 2] }.toFloat()
    val leftmost = sorted.minOf { it.leftPx }.toFloat()
    val rightmost = sorted.maxOf { (it.leftPx + it.widthPx).coerceAtMost(imageWidthPx) }.toFloat()
    val detectedWidth = (rightmost - leftmost).coerceAtLeast(imageWidthPx * 0.72f)
    val scaleX = 160f / detectedWidth
    val scaleY = 247f / imageHeightPx.coerceAtLeast(1).toFloat()
    val baselineLineHeightMm = (medianHeight * scaleY * 1.28f).coerceIn(4.2f, 7.2f)

    val sb = StringBuilder()
    sb.append(
        """
        <style>
            .ocr-flow {
                position: relative;
                width: 160mm;
                height: 247mm;
                margin: 0 auto;
                font-family: "Times New Roman", Times, serif;
                font-size: 12pt;
                line-height: 1.4;
            }
            .ocr-flow .ocr-line {
                position: absolute;
                margin: 0;
                padding: 0;
                white-space: pre-wrap;
                overflow-wrap: anywhere;
                text-indent: 0;
            }
            .ocr-flow .ocr-header {
                font-weight: 700;
                letter-spacing: 0.02em;
            }
            .ocr-flow .ocr-center { text-align: center; }
            .ocr-flow .ocr-left { text-align: left; }
            .ocr-flow .ocr-right { text-align: right; }
            .ocr-flow .ocr-justify { text-align: justify; }
        </style>
        <div class="ocr-flow">
        """.trimIndent()
    )

    val topAnchor = sorted.first().topPx
    val bottomRegionStart = imageHeightPx * 0.76f

    sorted.forEach { line ->
        val rowText = line.text.replace(Regex("\\s+"), " ").trim()
        if (rowText.isBlank()) return@forEach

        val textLeftMm = ((line.leftPx - leftmost).coerceAtLeast(0f) * scaleX).coerceIn(0f, 160f)
        val textTopMm = ((line.topPx - topAnchor).coerceAtLeast(0) * scaleY).coerceIn(0f, 245f)
        val textWidthMm = (line.widthPx * scaleX).coerceIn(14f, (160f - textLeftMm).coerceAtLeast(14f))
        val fontSizePt = (line.fontPx * scaleY * 2.85f).coerceIn(10f, 14f)
        val lineHeightMm = (line.heightPx * scaleY * 1.24f).coerceIn(4f, baselineLineHeightMm + 1.8f)

        val centerX = line.leftPx + (line.widthPx / 2f)
        val centered = centerX in (imageWidthPx * 0.33f)..(imageWidthPx * 0.67f)
        val nearRight = (line.leftPx + line.widthPx) >= imageWidthPx * 0.78f
        val isHeaderRegion = line.topPx <= topAnchor + (medianHeight * 8f)
        val isFooterRegion = line.topPx >= bottomRegionStart
        val alignClass = when {
            centered && isHeaderRegion && isLikelyOfficialHeaderLine(rowText) -> "ocr-center ocr-header"
            nearRight && isFooterRegion -> "ocr-right"
            line.widthPx >= (imageWidthPx * 0.58f) -> "ocr-justify"
            else -> "ocr-left"
        }
        sb.append("\n<p class=\"ocr-line $alignClass\" style=\"left:${"%.2f".format(java.util.Locale.US, textLeftMm)}mm; top:${"%.2f".format(java.util.Locale.US, textTopMm)}mm; width:${"%.2f".format(java.util.Locale.US, textWidthMm)}mm; min-height:${"%.2f".format(java.util.Locale.US, lineHeightMm)}mm; line-height:${"%.2f".format(java.util.Locale.US, lineHeightMm)}mm; font-size:${"%.2f".format(java.util.Locale.US, fontSizePt)}pt;\">")
        sb.append(
            rowText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        )
        sb.append("</p>")
    }

    sb.append("\n</div>")
    return sb.toString()
}

data class OcrPositionedText(
    val text: String,
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int
)

sealed class AiState {
    object Idle : AiState()
    object Loading : AiState()
    object Success : AiState()
    data class NeedsInput(val petitionData: AiGeneratedPetition) : AiState()
    data class Error(val message: String) : AiState()
}

sealed class GeminiOcrPreviewResult {
    data class Success(val confidence: Double) : GeminiOcrPreviewResult()
    data class Error(val message: String) : GeminiOcrPreviewResult()
}