package org.gpproject.backend.model;

public class OcrQueueResponse {

    // Current OCR request status: QUEUED, PROCESSING, COMPLETED, or FAILED.
    private final String status;

    // Ticket ID used by the client to poll OCR status.
    private final String ticketId;

    // OCR result payload when the request is completed.
    private final OcrLayoutResponse payload;

    // Optional error or status message.
    private final String message;

    public OcrQueueResponse(
            String status,
            String ticketId,
            OcrLayoutResponse payload,
            String message
    ) {
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