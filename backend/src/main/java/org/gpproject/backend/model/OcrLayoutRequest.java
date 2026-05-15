package org.gpproject.backend.model;

import lombok.Data;

@Data
public class OcrLayoutRequest {

    // Base64-encoded document image sent from Android.
    private String imageBase64;

    // Image MIME type, such as image/png or image/jpeg.
    private String mimeType;
}