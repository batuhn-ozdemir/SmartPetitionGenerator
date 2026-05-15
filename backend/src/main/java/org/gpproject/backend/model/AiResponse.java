package org.gpproject.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiResponse {

    // Current AI request status: PROCESSING, COMPLETED, or FAILED.
    private String status;

    // Ticket ID used by the client to poll the request status.
    private String ticketId;

    // AI output or error message.
    private String payload;
}