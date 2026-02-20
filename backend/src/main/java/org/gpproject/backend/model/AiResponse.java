package org.gpproject.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiResponse {
    private String status;      // PROCESSING, COMPLETED, FAILED
    private String ticketId;
    private String payload;     // HTML İçerik
}
