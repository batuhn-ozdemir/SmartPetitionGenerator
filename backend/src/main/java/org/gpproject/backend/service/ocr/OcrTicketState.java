package org.gpproject.backend.service.ocr;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.gpproject.backend.model.OcrLayoutResponse;

@Getter
@AllArgsConstructor
public class OcrTicketState {

    public enum Status {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    private final String ownerClientId;
    private final Status status;
    private final OcrLayoutResponse payload;
    private final String errorMessage;
    private final long createdAtMs;

    public OcrTicketState withState(
            Status newStatus,
            OcrLayoutResponse newPayload,
            String newErrorMessage
    ) {
        return new OcrTicketState(
                ownerClientId,
                newStatus,
                newPayload,
                newErrorMessage,
                createdAtMs
        );
    }
}