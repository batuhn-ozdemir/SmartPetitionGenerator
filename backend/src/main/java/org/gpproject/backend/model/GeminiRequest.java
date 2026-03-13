package org.gpproject.backend.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GeminiRequest {

    @SerializedName("system_instruction")
    private Content systemInstruction;  // ← Ayrı alan

    private Content[] contents;         // ← Sadece kullanıcı mesajı

    @SerializedName("generationConfig")
    private GenerationConfig generationConfig;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class Content {
        private Part[] parts;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class Part {
        private String text;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class GenerationConfig {
        @SerializedName("maxOutputTokens")
        private Integer maxOutputTokens;

        private Double temperature;
    }
}
