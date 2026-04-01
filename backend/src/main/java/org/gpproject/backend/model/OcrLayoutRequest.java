package org.gpproject.backend.model;

import lombok.Data;

@Data
public class OcrLayoutRequest {
    private String imageBase64;
    private String mimeType;
}
