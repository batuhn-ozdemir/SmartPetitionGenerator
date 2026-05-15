package org.gpproject.backend.controller;

import lombok.RequiredArgsConstructor;
import org.gpproject.backend.model.AiResponse;
import org.gpproject.backend.model.OcrLayoutRequest;
import org.gpproject.backend.model.OcrQueueResponse;
import org.gpproject.backend.model.UserPrompt;
import org.gpproject.backend.service.ocr.OcrQueueService;
import org.gpproject.backend.service.ocr.OcrTicketState;
import org.gpproject.backend.service.text.QueueService;
import org.gpproject.backend.service.text.TicketState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/petition")
@RequiredArgsConstructor
public class PetitionController {

    private final QueueService queueService;
    private final OcrQueueService ocrQueueService;

    // Normalizes the client ID sent by Android.
    // If no client ID is provided, the request is grouped under "anonymous".
    private String normalizeClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return "anonymous";
        }

        return clientId.trim();
    }

    // Starts an AI petition generation request.
    // The request is added to a queue and the client receives a ticket ID.
    @PostMapping("/generate")
    public ResponseEntity<AiResponse> generatePetition(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestBody UserPrompt prompt
    ) {
        String cid = normalizeClientId(clientId);

        try {
            String ticketId = queueService.addToQueue(cid, prompt.getText());

            return ResponseEntity.ok(
                    new AiResponse("PROCESSING", ticketId, null)
            );
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null
                    ? "Sunucu şu anda yoğun. Lütfen tekrar deneyin."
                    : e.getMessage();

            return ResponseEntity.ok(
                    new AiResponse("FAILED", null, msg)
            );
        }
    }

    // Checks the current status of an AI generation ticket.
    @GetMapping("/status/{ticketId}")
    public ResponseEntity<AiResponse> checkStatus(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @PathVariable String ticketId
    ) {
        String cid = normalizeClientId(clientId);

        TicketState ticket = queueService.getTicket(cid, ticketId);

        if (ticket == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                new AiResponse(
                        ticket.getStatus().name(),
                        ticketId,
                        ticket.getPayload()
                )
        );
    }

    // Adds an OCR layout analysis request to the OCR queue.
    @PostMapping("/ocr-layout/queue")
    public ResponseEntity<OcrQueueResponse> enqueueOcr(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestBody OcrLayoutRequest request
    ) {
        String cid = normalizeClientId(clientId);

        if (
                request == null ||
                        request.getImageBase64() == null ||
                        request.getImageBase64().isBlank()
        ) {
            return ResponseEntity.ok(
                    new OcrQueueResponse(
                            "FAILED",
                            null,
                            null,
                            "Fotoğraf gönderilemedi."
                    )
            );
        }

        String ticketId = ocrQueueService.addToQueue(
                cid,
                request.getImageBase64(),
                request.getMimeType()
        );

        return ResponseEntity.ok(
                new OcrQueueResponse("QUEUED", ticketId, null, null)
        );
    }

    // Checks the current status of an OCR layout analysis ticket.
    @GetMapping("/ocr-layout/status/{ticketId}")
    public ResponseEntity<OcrQueueResponse> checkOcrStatus(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @PathVariable String ticketId
    ) {
        String cid = normalizeClientId(clientId);

        OcrTicketState ticket = ocrQueueService.getTicket(cid, ticketId);

        if (ticket == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                new OcrQueueResponse(
                        ticket.getStatus().name(),
                        ticketId,
                        ticket.getPayload(),
                        ticket.getErrorMessage()
                )
        );
    }
}