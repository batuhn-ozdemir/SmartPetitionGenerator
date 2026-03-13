package org.gpproject.backend.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.gpproject.backend.model.GeminiRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GeminiService {

    private static final Map<String, String> INSTITUTION_ABBREVIATIONS = createInstitutionAbbreviationMap();

    // application.properties dosyasındaki virgülle ayrılmış listeyi alır
    @Value("#{'${gemini.api.keys}'.split(',')}")
    private List<String> apiKeys;

    // Sırayı güvenli bir şekilde takip etmek için
    private final AtomicInteger currentKeyIndex = new AtomicInteger(0);

    private static final String GEMINI_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final List<String> MODEL_FALLBACK_ORDER = Arrays.asList(
            //"gemini-2.5-pro"
            "gemini-2.5-flash-lite"
    );
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new Gson();

    // Havuzdan sıradaki API Key'i çeken metod
    private String getNextApiKey() {
        int index = currentKeyIndex.getAndUpdate(i -> (i + 1) % apiKeys.size());
        return apiKeys.get(index).trim();
    }
}
