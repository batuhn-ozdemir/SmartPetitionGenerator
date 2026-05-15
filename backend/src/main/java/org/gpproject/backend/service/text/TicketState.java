package org.gpproject.backend.service.text;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TicketState {

    public enum Status {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    private final String ownerClientId;
    private final Status status;
    private final String payload;
    private final long createdAtMs;

    public TicketState withStatus(Status s, String newPayload) {
        return new TicketState(
                ownerClientId,
                s,
                newPayload,
                createdAtMs
        );
    }
}