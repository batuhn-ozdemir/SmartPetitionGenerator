package org.gpproject.backend.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.gpproject.backend.model.OcrLayoutResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DocumentOcrService {

    @Value("#{'${gemini.api.keys}'.split(',')}")
    private List<String> apiKeys;

    private static final String OCR_MODEL = "gemini-2.5-flash";
    private static final String GEMINI_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final AtomicInteger currentKeyIndex = new AtomicInteger(0);
    private final Gson gson = new Gson();

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public OcrLayoutResponse analyzeDocumentLayout(String imageBase64, String mimeType) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            return fail("EMPTY_IMAGE", "Fotoğraf gönderilemedi.");
        }

        List<String> keys = normalizedApiKeys();
        if (keys.isEmpty()) {
            return fail("API_KEY_MISSING", "Gemini API anahtarı tanımlı değil.");
        }

        String resolvedMime = (mimeType == null || mimeType.isBlank())
                ? "image/jpeg"
                : mimeType.trim();

        String prompt = """
                You are a strict OCR layout extractor.
                Return ONLY one JSON object.

                Goal:
                Extract readable text lines from the main page/document in the image with their original pixel coordinates.

                Requirements:
                - Detect the main page/document area first.
                - Extract every readable text line that belongs to the page.
                - Keep the line text exactly as seen. Do not paraphrase.
                - Do not merge far apart lines.
                - Ignore logos, icons, seals, decorative lines, pure signature scribbles, and background clutter.
                - Keep handwritten words only if they are clearly readable.
                - Coordinates must be in ORIGINAL image pixel coordinates.
                - Sort lines in reading order: top-to-bottom, then left-to-right.
                - Form-style petition fields such as "İlçesi ...", "Adı Soyadı ...", "T.C. Kimlik No ..." are part of the document and must be extracted as readable lines.
                - Dotted or underscored fill-in blanks are valid text lines and must be kept.
                - If a line contains a fillable placeholder area made of repeated dots/underscores, preserve the label text and keep the placeholder compact.
                - Normalize any run of dots longer than 3 to exactly "..." in output text.
                - Normalize any run of underscores longer than 5 to exactly "_____".
                - Never invent extra dots/underscores that are not visible.
                - Keep each fillable field on its own original line.
                - Do not treat dotted fill-in blanks as decorative lines.

                Output JSON schema:
                {
                  "success": boolean,
                  "errorCode": "LOW_QUALITY|NOT_DOCUMENT|PARSING_ERROR|null",
                  "errorMessage": "string or null",
                  "confidence": number,
                  "petition": boolean,
                  "imageWidth": number,
                  "imageHeight": number,
                  "lines": [
                    {
                      "text": "string",
                      "leftPx": number,
                      "topPx": number,
                      "widthPx": number,
                      "heightPx": number,
                      "writingType": "PRINTED|HANDWRITTEN"
                    }
                  ]
                }

                Rules:
                - If there is no readable page/document, return success=false and errorCode=NOT_DOCUMENT.
                - If a page exists but text is mostly unreadable, return success=false and errorCode=LOW_QUALITY.
                - If extraction succeeds, return success=true.
                - Set petition=true when extraction succeeds on a readable document page.
                - Return JSON only. No markdown. No explanation.
                """;

        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");

        JsonArray parts = new JsonArray();

        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        parts.add(textPart);

        JsonObject imagePart = new JsonObject();
        JsonObject inlineData = new JsonObject();
        inlineData.addProperty("mime_type", resolvedMime);
        inlineData.addProperty("data", imageBase64);
        imagePart.add("inline_data", inlineData);
        parts.add(imagePart);

        userContent.add("parts", parts);
        contents.add(userContent);
        requestBody.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.0);
        generationConfig.addProperty("maxOutputTokens", 8192);
        generationConfig.addProperty("responseMimeType", "application/json");
        requestBody.add("generationConfig", generationConfig);

        String body = gson.toJson(requestBody);

        String lastErrorCode = "OCR_FAILED";
        String lastErrorMessage = "Belge analizi başarısız oldu.";
        long backoffMs = 1000L;

        for (int attempt = 0; attempt < 3; attempt++) {
            String apiKey = getNextApiKey(keys);
            String fullUrl = GEMINI_URL_TEMPLATE.formatted(OCR_MODEL) + "?key=" + apiKey;

            Request request = new Request.Builder()
                    .url(fullUrl)
                    .post(RequestBody.create(body, MediaType.get("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                int code = response.code();

                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    String providerError = extractProviderErrorMessage(responseBody);

                    if (code == 429) {
                        lastErrorCode = "RATE_LIMITED";
                        lastErrorMessage = providerError.isBlank()
                                ? "İstek limiti doldu. Lütfen tekrar deneyin."
                                : providerError;
                        sleepQuietly(parseRetryDelayMs(response, providerError, backoffMs));
                        backoffMs = Math.min(backoffMs * 2, 5000L);
                        continue;
                    }

                    if (code >= 500) {
                        lastErrorCode = "UPSTREAM_ERROR";
                        lastErrorMessage = providerError.isBlank()
                                ? "OCR servis hatası: " + code
                                : providerError;
                        sleepQuietly(backoffMs);
                        backoffMs = Math.min(backoffMs * 2, 5000L);
                        continue;
                    }

                    if (code == 413) {
                        return fail("PAYLOAD_TOO_LARGE", providerError.isBlank()
                                ? "Görsel boyutu servis limitini aştı."
                                : providerError);
                    }

                    return fail("MODEL_ERROR", providerError.isBlank()
                            ? "OCR isteği başarısız oldu: " + code
                            : providerError);
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                String aiRaw = extractModelText(responseBody);
                if (aiRaw == null || aiRaw.isBlank()) {
                    lastErrorCode = "PARSING_ERROR";
                    lastErrorMessage = "OCR cevabı boş döndü.";
                    sleepQuietly(backoffMs);
                    continue;
                }

                OcrLayoutResponse parsed = parseOcrLayoutFromModelText(aiRaw);
                if (parsed != null) {
                    normalizeLineTexts(parsed);
                    return parsed;
                }

                if (parsed == null) {
                    System.out.println("OCR RAW RESPONSE:\n" + aiRaw);
                    lastErrorCode = "PARSING_ERROR";
                    lastErrorMessage = "OCR cevabı işlenemedi.";
                    sleepQuietly(backoffMs);
                    continue;
                }

                normalizeResponse(parsed);

                if (!parsed.isSuccess()) {
                    return parsed;
                }

                if (parsed.getLines() == null || parsed.getLines().isEmpty()) {
                    return fail("LOW_QUALITY", "Okunabilir satır bulunamadı.");
                }

                parsed.setPetition(true);
                return parsed;

            } catch (Exception e) {
                lastErrorCode = "NETWORK_ERROR";
                lastErrorMessage = "Belge analizi sırasında bağlantı hatası oluştu.";
                sleepQuietly(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 5000L);
            }
        }

        return fail(lastErrorCode, lastErrorMessage);
    }

    private List<String> normalizedApiKeys() {
        if (apiKeys == null) return List.of();
        return apiKeys.stream()
                .map(k -> k == null ? "" : k.trim())
                .filter(k -> !k.isBlank())
                .filter(k -> !k.contains("YOUR_"))
                .collect(Collectors.toList());
    }

    private String getNextApiKey(List<String> keys) {
        int index = currentKeyIndex.getAndUpdate(i -> (i + 1) % keys.size());
        return keys.get(index);
    }

    private OcrLayoutResponse fail(String code, String message) {
        return new OcrLayoutResponse(false, code, message, 0.0, false, 0, 0, List.of());
    }

    private String extractModelText(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            return root.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
        } catch (Exception e) {
            return null;
        }
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

    private OcrLayoutResponse parseOcrLayoutFromModelText(String aiRaw) {
        if (aiRaw == null || aiRaw.isBlank()) return null;

        String normalized = aiRaw
                .replace("```json", "")
                .replace("```", "")
                .trim();

        List<String> candidates = new ArrayList<>();
        candidates.add(extractJsonIfAny(normalized));

        String balanced = extractFirstJsonObject(normalized);
        if (!balanced.isBlank()) candidates.add(balanced);

        String repaired = repairLikelyJson(extractJsonIfAny(normalized));
        if (!repaired.isBlank()) candidates.add(repaired);

        for (String candidate : candidates) {
            try {
                if (candidate == null || candidate.isBlank()) continue;
                JsonObject result = JsonParser.parseString(candidate).getAsJsonObject();
                return gson.fromJson(result, OcrLayoutResponse.class);
            } catch (Exception ex) {
                System.out.println("OCR_PARSE_CANDIDATE_FAIL:");
                System.out.println(candidate);
                ex.printStackTrace();
            }
        }

        return null;
    }

    private void normalizeLineTexts(OcrLayoutResponse response) {
        if (response == null || response.getLines() == null) return;

        for (OcrLayoutResponse.OcrLine line : response.getLines()) {
            if (line == null || line.getText() == null) continue;

            line.setText(
                    line.getText()
                            .replace('\u201c', '"')
                            .replace('\u201d', '"')
                            .replace('\u2018', '\'')
                            .replace('\u2019', '\'')
            );
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

    private String repairLikelyJson(String text) {
        if (text == null || text.isBlank()) return "";
        return text
                .replaceAll(",\\s*([}\\]])", "$1")
                .replaceAll("[\\u0000-\\u001F&&[^\\n\\r\\t]]", " ")
                .trim();
    }

    private String extractFirstJsonObject(String text) {
        if (text == null || text.isBlank()) return "";
        int start = text.indexOf('{');
        if (start < 0) return "";

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    escaped = true;
                    continue;
                }
                if (ch == '"') {
                    inString = false;
                }
                continue;
            }

            if (ch == '"') {
                inString = true;
                continue;
            }

            if (ch == '{') depth++;
            if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1).trim();
                }
            }
        }
        return "";
    }

    private void normalizeResponse(OcrLayoutResponse response) {
        if (response == null) return;

        if (response.getLines() == null) {
            response.setLines(new ArrayList<>());
            return;
        }

        List<OcrLayoutResponse.OcrLine> cleaned = response.getLines().stream()
                .filter(line -> line != null && line.getText() != null)
                .map(line -> {
                    String text = line.getText()
                            .replace('\r', ' ')
                            .replace('\n', ' ')
                            .replaceAll("\\s+", " ")
                            .trim();

                    line.setText(text);
                    line.setWidthPx(Math.max(1, line.getWidthPx()));
                    line.setHeightPx(Math.max(1, line.getHeightPx()));
                    line.setLeftPx(Math.max(0, line.getLeftPx()));
                    line.setTopPx(Math.max(0, line.getTopPx()));

                    if (line.getWritingType() == null || line.getWritingType().isBlank()) {
                        line.setWritingType("PRINTED");
                    }
                    return line;
                })
                .filter(line -> !line.getText().isBlank())
                .sorted(Comparator
                        .comparingInt(OcrLayoutResponse.OcrLine::getTopPx)
                        .thenComparingInt(OcrLayoutResponse.OcrLine::getLeftPx))
                .collect(Collectors.toList());

        response.setLines(cleaned);

        if (response.getConfidence() <= 0.0) {
            response.setConfidence(cleaned.isEmpty() ? 0.0 : 0.90);
        }

        if (response.getImageWidth() < 0) response.setImageWidth(0);
        if (response.getImageHeight() < 0) response.setImageHeight(0);

        if (response.getErrorCode() != null && response.getErrorCode().isBlank()) {
            response.setErrorCode(null);
        }
        if (response.getErrorMessage() != null && response.getErrorMessage().isBlank()) {
            response.setErrorMessage(null);
        }
    }

    private void sleepQuietly(long delayMs) {
        if (delayMs <= 0) return;
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}