package org.gpproject.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrLayoutResponse {
    private boolean success;
    private String errorCode;
    private String errorMessage;
    private double confidence;
    private boolean petition;
    private int imageWidth;
    private int imageHeight;
    private List<OcrLine> lines = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrLine {
        private String text;
        private int leftPx;
        private int topPx;
        private int widthPx;
        private int heightPx;
        private String writingType;
    }
}
