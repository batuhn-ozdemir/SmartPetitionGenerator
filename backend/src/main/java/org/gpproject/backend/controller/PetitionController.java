package org.gpproject.backend.controller;

import lombok.RequiredArgsConstructor;
import org.batuhanozdemir.smartpetitiongeneratorbackenddemo.model.AiResponse;
import org.batuhanozdemir.smartpetitiongeneratorbackenddemo.model.UserPrompt;
import org.batuhanozdemir.smartpetitiongeneratorbackenddemo.service.QueueService;
import org.batuhanozdemir.smartpetitiongeneratorbackenddemo.service.TicketState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/petition")
@RequiredArgsConstructor
public class PetitionController {

    private final QueueService queueService;

    // ✅ Çok kullanıcı: istemci kimliği header’dan gelir (Android bunu göndermeli)
    private String normalizeClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) return "anonymous";
        return clientId.trim();
    }

    @PostMapping("/generate")
    public ResponseEntity<AiResponse> generatePetition(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestBody UserPrompt prompt
    ) {
        String cid = normalizeClientId(clientId);

        try {
            String ticketId = queueService.addToQueue(cid, prompt.getText());
            return ResponseEntity.ok(new AiResponse("PROCESSING", ticketId, null));
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "Sunucu şu anda yoğun. Lütfen tekrar deneyin." : e.getMessage();
            return ResponseEntity.ok(new AiResponse("FAILED", null, msg));
        }
    }

    @GetMapping("/status/{ticketId}")
    public ResponseEntity<AiResponse> checkStatus(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @PathVariable String ticketId
    ) {
        String cid = normalizeClientId(clientId);

        TicketState ticket = queueService.getTicket(cid, ticketId);
        if (ticket == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(new AiResponse(ticket.getStatus().name(), ticketId, ticket.getPayload()));
    }
}
