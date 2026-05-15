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

    // Indicates whether OCR processing succeeded.
    private boolean success;

    // Optional machine-readable error code.
    private String errorCode;

    // Optional human-readable error message.
    private String errorMessage;

    // OCR confidence score.
    private double confidence;

    // Indicates whether the analyzed image looks like a petition document.
    private boolean petition;

    // Original analyzed image width.
    private int imageWidth;

    // Original analyzed image height.
    private int imageHeight;

    // OCR text lines with their bounding boxes.
    private List<OcrLine> lines = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrLine {

        // Recognized text on this line.
        private String text;

        // Left coordinate of the text box in pixels.
        private int leftPx;

        // Top coordinate of the text box in pixels.
        private int topPx;

        // Width of the text box in pixels.
        private int widthPx;

        // Height of the text box in pixels.
        private int heightPx;

        // Optional writing type, such as printed or handwritten.
        private String writingType;
    }
}