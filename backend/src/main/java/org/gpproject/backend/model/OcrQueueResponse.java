package org.gpproject.backend.model;

public class OcrQueueResponse {
    private final String status;
    private final String ticketId;
    private final OcrLayoutResponse payload;
    private final String message;

    public OcrQueueResponse(String status, String ticketId, OcrLayoutResponse payload, String message) {
        this.status = status;
        this.ticketId = ticketId;
        this.payload = payload;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public String getTicketId() {
        return ticketId;
    }

    public OcrLayoutResponse getPayload() {
        return payload;
    }

    public String getMessage() {
        return message;
    }
}