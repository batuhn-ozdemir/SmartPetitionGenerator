package org.gpproject.backend.service;

public class TicketState {

    public enum Status { PROCESSING, COMPLETED, FAILED }

    private final String ownerClientId;
    private final Status status;
    private final String payload;
    private final long createdAtMs;

    public TicketState(String ownerClientId, Status status, String payload, long createdAtMs) {
        this.ownerClientId = ownerClientId;
        this.status = status;
        this.payload = payload;
        this.createdAtMs = createdAtMs;
    }

    public String getOwnerClientId() { return ownerClientId; }
    public Status getStatus() { return status; }
    public String getPayload() { return payload; }
    public long getCreatedAtMs() { return createdAtMs; }

    public TicketState withStatus(Status s, String newPayload) {
        return new TicketState(ownerClientId, s, newPayload, createdAtMs);
    }
}
