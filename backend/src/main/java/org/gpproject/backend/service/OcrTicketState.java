package org.gpproject.backend.service;

import org.gpproject.backend.model.OcrLayoutResponse;

public class OcrTicketState {

    public enum Status { QUEUED, PROCESSING, COMPLETED, FAILED }

    private final String ownerClientId;
    private final Status status;
    private final OcrLayoutResponse payload;
    private final String errorMessage;
    private final long createdAtMs;

    public OcrTicketState(String ownerClientId,
                          Status status,
                          OcrLayoutResponse payload,
                          String errorMessage,
                          long createdAtMs) {
        this.ownerClientId = ownerClientId;
        this.status = status;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.createdAtMs = createdAtMs;
    }

    public String getOwnerClientId() {
        return ownerClientId;
    }

    public Status getStatus() {
        return status;
    }

    public OcrLayoutResponse getPayload() {
        return payload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public OcrTicketState withState(Status newStatus, OcrLayoutResponse newPayload, String newErrorMessage) {
        return new OcrTicketState(ownerClientId, newStatus, newPayload, newErrorMessage, createdAtMs);
    }
}