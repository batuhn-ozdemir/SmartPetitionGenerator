package org.gpproject.backend.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GeminiService {

    private static final Map<String, String> INSTITUTION_ABBREVIATIONS = createInstitutionAbbreviationMap();

    @Value("#{'${gemini.api.keys}'.split(',')}")
    private List<String> apiKeys;

    private final AtomicInteger currentKeyIndex = new AtomicInteger(0);

    private static final String GEMINI_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final String TEXT_MODEL = "gemini-2.5-flash";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new Gson();

    private List<String> normalizedApiKeys() {
        List<String> keys = new ArrayList<>();

        if (apiKeys == null) return keys;

        for (String key : apiKeys) {
            if (key == null) continue;

            String cleaned = key.trim();

            if (cleaned.isBlank()) continue;
            if (cleaned.contains("YOUR_")) continue;

            keys.add(cleaned);
        }

        return keys;
    }

    private String getNextApiKey(List<String> keys) {
        int index = currentKeyIndex.getAndUpdate(i -> (i + 1) % keys.size());
        return keys.get(index);
    }

    public String callGemini(String userText) {
        String currentDate = LocalDate.now(ZoneId.of("Europe/Istanbul"))
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        boolean isFinal = userText != null && userText.trim().startsWith("FINAL_BUILD:");

        String systemInstruction = """
            You are an intelligent Turkish legal petition architect. 
            Today's date is %s.

            --- 1. BANNED PARAMS (DO NOT ASK FOR THESE) ---
            The system ALREADY has the user's personal profile.
            NEVER put these in `requiredParams`: AD_SOYAD, TCKN, TELEFON, ADRES, E-POSTA, SINIF, OGRENCI_NO, IMZA.
            Ask ONLY for context-specific missing data (normally 2-4 fields).
                
          --- 2. PETITION WRITING RULES (MANDATORY) ---
          Follow formal Turkish petition conventions:
          - Start with the relevant authority/institution (not a person) and keep hierarchical tone.
          - Use objective, concise, legally appropriate statements; avoid unnecessary personal narrative.
          - Use clear, correct Turkish grammar and punctuation.
          - If institution/authority is given as abbreviation or with obvious typo/missing suffix, expand it to the official formal form when confidently known.
          - Include place/date and end with suitable official closing (e.g., "gereğini arz ederim" / "bilgilerinize arz ederim").
          - Keep signature/name on the right; contact/address block on the left.
          - "KURUM_ADI" and "MAKAMIN_ADI" must be rendered on separate lines in the header, never as a single merged line.
          - "KURUM_ADI" must be plain institution display text (no addressee suffix like "-na/-ne").
          - "MAKAMIN_ADI" should be the formal addressee line (can include dative ending like "Müdürlüğüne").
          - Determine attachment need by topic context. In DRAFT, if attachments are likely relevant, ask the user with `EK_VAR_MI` (Evet/Hayır) and `EKLER_LISTESI`.
          - In FINAL_BUILD, include an explicit "Ek:" section with bullet items ONLY when attachments are provided/confirmed; otherwise do not render an Ek block.
        - Do not exceed legal boundaries and do not fabricate evidence, witnesses, dates, or events.
          
            Additional mandatory style checks:
          - Mention place and date.
          - Petition owner must include full contact address.
          - Use only official abbreviations when necessary; otherwise write full forms.
          - Final line must use proper hierarchy wording: citizens use "arz ederim", avoid "saygıyla arz" duplication.
          
          --- 3. SMART DEFAULTS FOR EMPTY FIELDS (CRITICAL) ---
          In FINAL_BUILD, if any placeholder value is missing/empty, infer the closest suitable formal value from petition topic and context.
            Prioritize MAKAMIN_ADI and KURUM_ADI defaults when user leaves them empty.
          - Academic context header defaults: "İLGİLİ DEKANLIĞA" or "İLGİLİ ENSTİTÜ MÜDÜRLÜĞÜNE".
          - Legal objection header default: "İLGİLİ NÖBETÇİ SULH CEZA HAKİMLİĞİNE".
            For other missing placeholders, produce safe, generic, non-fabricated formal wording (e.g., "ilgili ders", "ilgili birim", "ilgili işlem").
          Never fabricate personal identifiers (TCKN, student no, phone, address), exact evidence IDs, exact timeline/date ranges, or case numbers.

          --- 4. FLUID SENTENCE INTEGRATION (CRITICAL) ---
          When generating the body content in FINAL_BUILD, DO NOT copy-paste the user's raw input.
          Weave it into flowing, highly formal, and continuous Turkish sentences.
          NEVER use "..." or "___" placeholders.
          
        --- 4.1 SAFETY / SCOPE GATE (CRITICAL) ---
        You are ONLY a petition generator.
        If user asks for dangerous/abusive content (e.g., key theft, personal data exfiltration, hacking, bypass, unauthorized access), return EXACTLY:
        REJECT_SECURITY
        If user input is benign but outside petition scope (e.g., math/chat/general Q&A like "2+2 kaçtır"), return EXACTLY:
        REJECT_OUT_OF_SCOPE
        Do not add extra words in these two rejection cases.

        --- 5. A4 LAYOUT & POSITIONING ---
        You MUST use this EXACT HTML structure for the output:
        <div class='a4-page'>
            <div class='institution-name'>{{KURUM_ADI}}</div>
            <div class='recipient-header'>{{MAKAMIN_ADI}}</div>
            <div class='subject-line'><b>Konu:</b> {{KONU}}</div>
            <div class='body-content'>
                <p>... fluid, professional paragraphs here ...</p>
            </div>
            {{EK_BOLUMU}}
            <div class='footer-wrapper' style='display: flex; justify-content: space-between; align-items: flex-end; margin-top: 60px;'>
                <div class='contact-info' style='text-align: left; font-size: 13px; line-height: 1.5;'>
                    <div><b>Adres:</b> {{ADRES}}</div>
                    <div><b>Tel:</b> {{TELEFON}}</div>
                    <div><b>TCKN:</b> {{TCKN}}</div>
                </div>
                <div class='signature-section' style='text-align: center;'>
                    <div class='date-text'>%s</div>
                    <div class='name-text' style='font-weight: bold; margin-top: 40px;'>{{AD_SOYAD}}</div>
                </div>
            </div>
        </div>

            --- MODE 1: DRAFT (NO 'FINAL_BUILD:') ---
            In requiredParams, `key` must stay machine-friendly (UPPER_SNAKE_CASE), but `label` must be plain human Turkish (e.g., "Ders Adı", not "DERS_ADI").
            Return STRICT JSON: { "templateHtml": "...", "givenParams": {...}, "requiredParams": [...] }
            NEVER use bracket placeholders like [Ders Adı], (Mazeret), ___, ... inside templateHtml.
            For every variable data, use only {{KEY}} placeholder format and ensure missing keys exist in requiredParams.
            If the user explicitly provides a value (especially course/lesson name), include it in givenParams with the correct key.
            Never fabricate missing values in givenParams. Unknown values must stay in requiredParams.
                
            
            --- MODE 2: FINAL (STARTS WITH 'FINAL_BUILD:') ---
            Return ONLY STRICT HTML using the structure above. Integrate the user's data fluidly.
            """.formatted(currentDate, currentDate);

        String jsonBody = buildGeminiTextRequestBody(systemInstruction, userText, isFinal);

        List<String> keys = normalizedApiKeys();

        if (keys.isEmpty()) {
            return generateErrorJson("Gemini API anahtarı tanımlı değil.");
        }

        int maxAttempts = Math.max(keys.size() * 2, 2);
        long backoffMs = 750L;

        String lastError = "Sunucu Hatası: Tüm denemeler başarısız oldu.";

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String currentKey = getNextApiKey(keys);
            String fullUrl = GEMINI_URL_TEMPLATE.formatted(TEXT_MODEL) + "?key=" + currentKey;

            Request request = new Request.Builder()
                    .url(fullUrl)
                    .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    String providerError = extractProviderErrorMessage(responseBody);

                    lastError = providerError.isBlank()
                            ? "Sunucu Hatası: " + code + " (" + TEXT_MODEL + ")"
                            : providerError + " (" + TEXT_MODEL + ")";

                    System.out.println("=== GEMINI HTTP ERROR ===");
                    System.out.println("HTTP Code: " + code);
                    System.out.println("Model: " + TEXT_MODEL);
                    System.out.println("Response Body: " + responseBody);

                    if (code == 429) {
                        sleepQuietly(parseRetryDelayMs(response, providerError, backoffMs));
                        backoffMs = Math.min(backoffMs * 2, 5000L);
                        continue;
                    }

                    if (code >= 500) {
                        sleepQuietly(backoffMs);
                        backoffMs = Math.min(backoffMs * 2, 5000L);
                        continue;
                    }

                    // 400 / 403 / 404 gibi hatalarda model değiştirmiyoruz.
                    // Sadece varsa diğer API key denenir.
                    continue;
                }

                String aiRawText = extractModelText(responseBody);

                if (aiRawText == null || aiRawText.isBlank()) {
                    String finishReason = extractFinishReason(responseBody);

                    lastError = finishReason.isBlank()
                            ? "Yapay zeka cevabı boş döndü."
                            : "Yapay zeka cevabı boş döndü. FinishReason: " + finishReason;

                    System.out.println("=== GEMINI EMPTY TEXT RESPONSE ===");
                    System.out.println("Model: " + TEXT_MODEL);
                    System.out.println("Response Body: " + responseBody);

                    continue;
                }

                System.out.println("=== GEMINI MODEL USED ===");
                System.out.println(TEXT_MODEL);
                System.out.println("=== GEMINI RAW TEXT ===");
                System.out.println(aiRawText);

                String cleanText = aiRawText
                        .replace("```json", "")
                        .replace("```html", "")
                        .replace("```", "")
                        .trim();

                if (cleanText.trim().equals("REJECT_SECURITY")) {
                    return generateErrorJson("Güvenlik Reddi: Lütfen sadece resmi yazışma konuları giriniz.");
                }

                if (cleanText.trim().equals("REJECT_OUT_OF_SCOPE")) {
                    return generateErrorJson("Görev dışı işlem: Bu servis yalnızca dilekçe oluşturma taleplerini işler.");
                }

                cleanText = extractJsonIfAny(cleanText);

                if (!isFinal && cleanText.startsWith("{")) {
                    return normalizeDraftJson(cleanText, currentDate, userText);
                }

                if (cleanText.startsWith("{") && cleanText.endsWith("}")) {
                    return cleanText;
                }

                if (cleanText.contains("<div") || cleanText.contains("<p>")) {
                    return cleanText;
                }

                System.out.println("=== INVALID FORMAT AFTER CLEAN ===");
                System.out.println(cleanText);

                lastError = "Yapay zeka geçerli bir format üretemedi.";

            } catch (Exception e) {
                lastError = "Bağlantı Hatası: " + e.getMessage();

                System.out.println("=== GEMINI CONNECTION ERROR ===");
                e.printStackTrace();

                sleepQuietly(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 5000L);
            }
        }

        System.out.println("=== GEMINI FINAL ERROR ===");
        System.out.println(lastError);

        return generateErrorJson(lastError);
    }

    private String buildGeminiTextRequestBody(String systemInstructionText, String userText, boolean isFinal) {
        JsonObject root = new JsonObject();

        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();

        JsonObject systemTextPart = new JsonObject();
        systemTextPart.addProperty("text", systemInstructionText);
        systemParts.add(systemTextPart);

        systemInstruction.add("parts", systemParts);
        root.add("systemInstruction", systemInstruction);

        JsonArray contents = new JsonArray();

        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");

        JsonArray userParts = new JsonArray();

        JsonObject userTextPart = new JsonObject();
        userTextPart.addProperty("text", userText == null ? "" : userText);
        userParts.add(userTextPart);

        userContent.add("parts", userParts);
        contents.add(userContent);

        root.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.2);

        // Eski 1400 / 1800 token Flash için az kalabiliyordu.
        generationConfig.addProperty("maxOutputTokens", isFinal ? 8192 : 4096);

        // DRAFT modunda JSON bekliyoruz.
        // FINAL_BUILD modunda HTML beklediğimiz için JSON mode açmıyoruz.
        if (!isFinal) {
            generationConfig.addProperty("responseMimeType", "application/json");
        }

        root.add("generationConfig", generationConfig);

        return gson.toJson(root);
    }

    private String extractModelText(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();

            if (!root.has("candidates") || !root.get("candidates").isJsonArray()) {
                return "";
            }

            JsonArray candidates = root.getAsJsonArray("candidates");

            if (candidates.isEmpty()) {
                return "";
            }

            JsonObject firstCandidate = candidates.get(0).getAsJsonObject();

            if (!firstCandidate.has("content") || !firstCandidate.get("content").isJsonObject()) {
                return "";
            }

            JsonObject content = firstCandidate.getAsJsonObject("content");

            if (!content.has("parts") || !content.get("parts").isJsonArray()) {
                return "";
            }

            JsonArray parts = content.getAsJsonArray("parts");

            if (parts.isEmpty()) {
                return "";
            }

            JsonObject firstPart = parts.get(0).getAsJsonObject();

            if (!firstPart.has("text") || firstPart.get("text").isJsonNull()) {
                return "";
            }

            return firstPart.get("text").getAsString();

        } catch (Exception e) {
            return "";
        }
    }

    private String extractFinishReason(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();

            if (!root.has("candidates") || !root.get("candidates").isJsonArray()) {
                return "";
            }

            JsonArray candidates = root.getAsJsonArray("candidates");

            if (candidates.isEmpty()) {
                return "";
            }

            JsonObject firstCandidate = candidates.get(0).getAsJsonObject();

            if (firstCandidate.has("finishReason") && !firstCandidate.get("finishReason").isJsonNull()) {
                return firstCandidate.get("finishReason").getAsString();
            }

        } catch (Exception ignored) {
        }

        return "";
    }

    private long parseRetryDelayMs(Response response, String providerError, long fallbackMs) {
        String retryHeader = response.header("Retry-After");

        if (retryHeader != null) {
            try {
                long sec = Long.parseLong(retryHeader.trim());
                if (sec > 0) return sec * 1000L;
            } catch (Exception ignored) {
            }
        }

        if (providerError != null && !providerError.isBlank()) {
            Matcher matcher = Pattern.compile("(?i)(\\d+)\\s*(saniye|seconds?)").matcher(providerError);

            if (matcher.find()) {
                try {
                    return Long.parseLong(matcher.group(1)) * 1000L;
                } catch (Exception ignored) {
                }
            }
        }

        return Math.max(1000L, fallbackMs);
    }

    private void sleepQuietly(long delayMs) {
        if (delayMs <= 0) return;

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String extractJsonIfAny(String text) {
        if (text == null) return "";
        String t = text.trim();
        int first = t.indexOf('{');
        int last = t.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return t.substring(first, last + 1).trim();
        }
        return t;
    }

    private String extractProviderErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "";
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            if (!root.has("error") || !root.get("error").isJsonObject()) return "";
            JsonObject error = root.getAsJsonObject("error");
            if (error.has("message") && !error.get("message").isJsonNull()) {
                return error.get("message").getAsString();
            }
        } catch (Exception ignored) {
            // no-op
        }
        return "";
    }

    private String normalizeDraftJson(String json, String currentDate, String userText) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            if (!obj.has("templateHtml") || obj.get("templateHtml").isJsonNull() || obj.get("templateHtml").getAsString().trim().isEmpty()) {
                obj.addProperty("templateHtml",
                        "<div class='a4-page'>" +
                                "<div class='institution-name'>{{KURUM_ADI}}</div>" +
                                "<div class='recipient-header'>{{MAKAMIN_ADI}}</div>" +
                                "<div class='subject-line'><b>Konu:</b> {{KONU}}</div>" +
                                "<div class='body-content'><p>{{ACIKLAMA}}</p></div>" +
                                "{{EK_BOLUMU}}" +
                                "<div class='footer-wrapper' style='display: flex; justify-content: space-between; align-items: flex-end; margin-top: 60px;'>" +
                                "<div class='contact-info' style='text-align: left; font-size: 13px; line-height: 1.5;'>" +
                                "<div><b>Adres:</b> {{ADRES}}</div><div><b>Tel:</b> {{TELEFON}}</div><div><b>TCKN:</b> {{TCKN}}</div></div>" +
                                "<div class='signature-section' style='text-align: center;'>" +
                                "<div class='date-text'>" + currentDate + "</div><div class='name-text' style='font-weight: bold; margin-top: 40px;'>{{AD_SOYAD}}</div></div>" +
                                "</div></div>");
            }

            if (!obj.has("givenParams") || !obj.get("givenParams").isJsonObject()) {
                obj.add("givenParams", new JsonObject());
            }

            sanitizeGivenParams(obj, userText);

            if (!obj.has("requiredParams") || !obj.get("requiredParams").isJsonArray()) {
                obj.add("requiredParams", new JsonArray());
            }

            JsonArray arr = obj.getAsJsonArray("requiredParams");
            JsonArray fixed = new JsonArray();

            for (JsonElement el : arr) {
                if (el == null || el.isJsonNull()) continue;
                if (el.isJsonObject()) {
                    JsonObject field = el.getAsJsonObject();
                    String rawKey = field.has("key") ? field.get("key").getAsString() : null;
                    String key = sanitizeKey(rawKey);
                    if (key.isEmpty()) continue;

                    // Yasaklı olanları forma dahil etme
                    if (key.equals("AD_SOYAD") || key.equals("TCKN") || key.equals("TELEFON") || key.equals("ADRES")) continue;

                    field.addProperty("key", key);
                    if (!field.has("label") || field.get("label").isJsonNull()) field.addProperty("label", key);
                    if (!field.has("type") || field.get("type").isJsonNull()) field.addProperty("type", "text");
                    fixed.add(field);
                }
            }

            obj.add("requiredParams", fixed);

            ensureInstitutionLabels(obj);
            ensureAttachmentFields(obj, userText);
            return gson.toJson(obj);

        } catch (Exception e) {
//            System.out.println("=== NORMALIZE DRAFT JSON ERROR ===");
//            e.printStackTrace();
//            System.out.println("=== BROKEN JSON INPUT ===");
//            System.out.println(json);
            return generateErrorJson("Taslak JSON normalize edilemedi.");
        }
    }

    private void ensureInstitutionLabels(JsonObject obj) {
        if (!obj.has("requiredParams") || !obj.get("requiredParams").isJsonArray()) return;

        JsonArray required = obj.getAsJsonArray("requiredParams");
        ensureRequiredField(required, "KURUM_ADI", "Kurum Adı", "text");
        ensureRequiredField(required, "MAKAMIN_ADI", "Makam / Kurum", "text");
    }

    private void ensureAttachmentFields(JsonObject obj, String userText) {
        if (!obj.has("requiredParams") || !obj.get("requiredParams").isJsonArray()) return;

        JsonArray required = obj.getAsJsonArray("requiredParams");
        if (needsAttachmentInput(userText)) {
            ensureRequiredField(required, "EK_VAR_MI", "Ek var mı? (Evet/Hayır)", "text");
            ensureRequiredField(required, "EKLER_LISTESI", "Ekler (varsa her satıra bir ek)", "text");
        }
    }

    private void sanitizeGivenParams(JsonObject obj, String userText) {
        JsonObject source = obj.getAsJsonObject("givenParams");
        JsonObject cleaned = new JsonObject();

        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String key = sanitizeKey(entry.getKey());
            if (key.isBlank()) continue;
            JsonElement valueEl = entry.getValue();
            if (valueEl == null || valueEl.isJsonNull()) continue;

            String value = valueEl.getAsString().trim();
            if (value.isBlank() || looksLikePlaceholder(value)) continue;
            if (!shouldKeepGivenValue(key, value, userText)) continue;

            cleaned.addProperty(key, maybeExpandInstitutionValue(key, value));
        }

        enrichGivenParamsFromPrompt(cleaned, userText);

        obj.add("givenParams", cleaned);
    }

    private void enrichGivenParamsFromPrompt(JsonObject cleaned, String userText) {
        if (userText == null || userText.isBlank()) return;

        Matcher keyValueMatcher = Pattern.compile("(?iu)([\\p{L}][\\p{L}0-9_ ]{1,40})\\s*(?::|：|\\s-\\s)\\s*([^,;\\n\\r]{2,120})").matcher(userText);
        while (keyValueMatcher.find()) {
            String rawLabel = keyValueMatcher.group(1).trim();
            String rawValue = keyValueMatcher.group(2).trim();

            if (looksLikePlaceholder(rawValue)) continue;
            String dynamicKey = sanitizeKey(rawLabelToKey(rawLabel));
            if (dynamicKey.isBlank() || isBannedKey(dynamicKey)) continue;

            cleaned.addProperty(dynamicKey, maybeExpandInstitutionValue(dynamicKey, rawValue));
        }

        if (cleaned.has("KURUM_ADI")) {
            String kurum = cleaned.get("KURUM_ADI").getAsString();
            cleaned.addProperty("KURUM_ADI", maybeExpandInstitutionValue("KURUM_ADI", kurum));
        }
        if (cleaned.has("MAKAMIN_ADI")) {
            String makam = cleaned.get("MAKAMIN_ADI").getAsString();
            cleaned.addProperty("MAKAMIN_ADI", maybeExpandInstitutionValue("MAKAMIN_ADI", makam));
        }
    }

    private String rawLabelToKey(String rawLabel) {
        return rawLabel.toUpperCase(Locale.ROOT)
                .replace('Ç', 'C')
                .replace('Ğ', 'G')
                .replace('İ', 'I')
                .replace('Ö', 'O')
                .replace('Ş', 'S')
                .replace('Ü', 'U')
                .replace('ç', 'c')
                .replace('ğ', 'g')
                .replace('ı', 'i')
                .replace('ö', 'o')
                .replace('ş', 's')
                .replace('ü', 'u')
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", "_")
                .trim();
    }

    private String maybeExpandInstitutionValue(String key, String value) {
        if (value == null || value.isBlank()) return value;
        if (!("KURUM_ADI".equals(key) || "MAKAMIN_ADI".equals(key))) return value;

        String normalized = normalizeForMatch(value);
        if (INSTITUTION_ABBREVIATIONS.containsKey(normalized)) {
            return INSTITUTION_ABBREVIATIONS.get(normalized);
        }

        return value;
    }

    private static Map<String, String> createInstitutionAbbreviationMap() {
        Map<String, String> map = new HashMap<>();
        map.put("meb", "T.C. Millî Eğitim Bakanlığı");
        map.put("sgk", "Sosyal Güvenlik Kurumu Başkanlığı");
        map.put("yok", "Yükseköğretim Kurulu Başkanlığı");
        map.put("ytu", "Yıldız Teknik Üniversitesi Rektörlüğü");
        map.put("itu", "İstanbul Teknik Üniversitesi Rektörlüğü");
        map.put("odtu", "Orta Doğu Teknik Üniversitesi Rektörlüğü");
        map.put("deu", "Dokuz Eylül Üniversitesi Rektörlüğü");
        map.put("au", "Ankara Üniversitesi Rektörlüğü");
        map.put("fbe", "Fen Bilimleri Enstitüsü Müdürlüğü");
        map.put("sbe", "Sosyal Bilimler Enstitüsü Müdürlüğü");
        map.put("saglik bakanligi", "T.C. Sağlık Bakanlığı");
        return map;
    }

    private boolean valueMentionedInPrompt(String value, String userText) {
        if (userText == null || userText.isBlank()) return false;
        String cleanedValue = normalizeForMatch(value);
        if (cleanedValue.isBlank() || cleanedValue.length() < 2) return false;
        String cleanedPrompt = normalizeForMatch(userText);
        return cleanedPrompt.contains(cleanedValue);
    }

    private boolean shouldKeepGivenValue(String key, String value, String userText) {
        if (value == null || value.isBlank()) return false;
        if ("KURUM_ADI".equals(key) || "MAKAMIN_ADI".equals(key) || "KONU".equals(key)) {
            return true;
        }
        return valueMentionedInPrompt(value, userText);
    }

    private String normalizeForMatch(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replace('İ', 'i')
                .replaceAll("[^\\p{L}0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean needsAttachmentInput(String userText) {
        if (userText == null || userText.isBlank()) return false;
        String t = normalizeForMatch(userText);

        String[] attachmentKeywords = {
                "ek", "ekler", "belge", "belgeler", "rapor", "dekont", "makbuz",
                "tutanak", "tebligat", "fatura", "delil", "kanit", "epikriz", "tanik"
        };

        for (String keyword : attachmentKeywords) {
            if (t.contains(keyword)) return true;
        }
        return false;
    }

    private boolean looksLikePlaceholder(String value) {
        String v = value.trim();
        return v.contains("{{") || v.contains("}}") || v.contains("___") || v.contains("...") || v.equalsIgnoreCase("bilinmiyor");

    }

    private boolean isBannedKey(String key) {
        return key.equals("AD_SOYAD") || key.equals("TCKN") || key.equals("TELEFON") || key.equals("ADRES")
                || key.equals("E_POSTA") || key.equals("SINIF") || key.equals("OGRENCI_NO") || key.equals("IMZA");
    }

    private void ensureRequiredField(JsonArray required, String key, String label, String type) {
        for (JsonElement el : required) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("key") && key.equals(sanitizeKey(obj.get("key").getAsString()))) return;
        }

        JsonObject f = new JsonObject();
        f.addProperty("key", key);
        f.addProperty("label", label);
        f.addProperty("type", type);
        required.add(f);
    }

    private String sanitizeKey(String rawKey) {
        if (rawKey == null) return "";
        String key = rawKey.trim().toUpperCase(Locale.ROOT);
        while (key.startsWith("_")) key = key.substring(1);
        while (key.endsWith("_")) key = key.substring(0, key.length() - 1);
        return key.replace(' ', '_');
    }

    private String generateErrorJson(String msg) {
        String normalized = normalizeErrorMessage(msg);
        String safeMsg = htmlEscape(safeText(normalized));
        return "{ \"templateHtml\": \"<h3 style='text-align:center; color:red;'>HATA</h3><p>" + safeMsg + "</p>\", \"givenParams\": {}, \"requiredParams\": [] }";
    }

    private String normalizeErrorMessage(String msg) {
        String m = msg == null ? "" : msg.toLowerCase(Locale.ROOT);
        if (m.contains("şüpheli") || m.contains("supheli")
                || m.contains("güvenlik") || m.contains("security") || m.contains("reject")
                || m.contains("api key") || m.contains("api_key") || m.contains("anahtar")) {
            return "Şüpheli işlem. Lütfen talebinizi resmi dilekçe formatında yeniden giriniz.";
        }
        if (m.contains("görev dışı") || m.contains("gorev disi")
                || m.contains("out_of_scope") || m.contains("out of scope")
                || m.contains("yalnızca dilekçe") || m.contains("yalnizca dilekce")) {
            return "Görev dışı işlem: Bu servis yalnızca dilekçe oluşturma taleplerini işler.";
        }
        return "İşlem şu anda tamamlanamadı. Lütfen tekrar deneyiniz.";
    }

    private String safeText(String s) {
        return s == null ? "" : s.replace("\n", " ").replace("\r", " ").replace("\"", "'");
    }

    private String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}