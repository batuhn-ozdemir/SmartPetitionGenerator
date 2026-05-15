package org.gpproject.backend.service.ocr;

import org.gpproject.backend.model.OcrLayoutResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OcrQueueService {

    // Represents the actual OCR job data stored for a ticket.
    private record OcrJob(String imageBase64, String mimeType) {}

    private final DocumentOcrService documentOcrService;

    // Stores OCR ticket states by ticket ID.
    private final ConcurrentHashMap<String, OcrTicketState> tickets = new ConcurrentHashMap<>();

    // Stores OCR job payloads by ticket ID.
    private final ConcurrentHashMap<String, OcrJob> ticketJobs = new ConcurrentHashMap<>();

    // Keeps a separate queue for each client.
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> userQueues = new ConcurrentHashMap<>();

    // Stores client IDs waiting to be processed in round-robin order.
    private final ConcurrentLinkedQueue<String> roundRobinUsers = new ConcurrentLinkedQueue<>();

    // Prevents adding the same client multiple times to the round-robin queue.
    private final Set<String> usersInRoundRobin = ConcurrentHashMap.newKeySet();

    // Limits how many OCR jobs can run at the same time.
    private static final int MAX_CONCURRENT_OCR_WORKERS = 2;
    private final Semaphore workerPermits = new Semaphore(MAX_CONCURRENT_OCR_WORKERS);

    // Worker pool used to process OCR jobs in the background.
    private final ExecutorService executor = Executors.newFixedThreadPool(
            MAX_CONCURRENT_OCR_WORKERS,
            new ThreadFactory() {
                private final AtomicInteger i = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "ocr-worker-" + i.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            }
    );

    // Minimum delay between OCR model calls.
    private static final long MIN_INTERVAL_MS = 1500L;
    private final Object rateLock = new Object();
    private long lastCallMs = 0L;

    // Ticket lifetime before cleanup.
    private static final long TTL_MS = 2L * 60L * 60L * 1000L;

    public OcrQueueService(DocumentOcrService documentOcrService) {
        this.documentOcrService = documentOcrService;
    }

    // Simple queue log helper.
    private void qlog(String event, String clientId, String ticketId, String extra) {
        System.out.printf(
                "[OCR-QUEUE] event=%s client=%s ticket=%s %s%n",
                event,
                clientId,
                ticketId,
                extra == null ? "" : extra
        );
    }

    // Adds a new OCR request to the client's queue and returns a ticket ID.
    public String addToQueue(String clientId, String imageBase64, String mimeType) {
        userQueues.putIfAbsent(clientId, new ConcurrentLinkedQueue<>());
        ConcurrentLinkedQueue<String> q = userQueues.get(clientId);

        String ticketId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        tickets.put(ticketId, new OcrTicketState(
                clientId,
                OcrTicketState.Status.QUEUED,
                null,
                null,
                now
        ));

        ticketJobs.put(ticketId, new OcrJob(imageBase64, mimeType));
        q.add(ticketId);

        // Add this client to round-robin scheduling only once.
        if (usersInRoundRobin.add(clientId)) {
            roundRobinUsers.add(clientId);
        }

        qlog("ADD", clientId, ticketId,
                "userQueueSize=" + q.size() +
                        " roundRobinUsers=" + roundRobinUsers.size() +
                        " totalTickets=" + tickets.size());

        return ticketId;
    }

    // Returns ticket state only if it belongs to the requesting client.
    public OcrTicketState getTicket(String clientId, String ticketId) {
        OcrTicketState st = tickets.get(ticketId);
        if (st == null) return null;

        if (!"anonymous".equals(clientId) && !st.getOwnerClientId().equals(clientId)) {
            return null;
        }

        return st;
    }

    // Periodically dispatches queued OCR jobs to worker threads.
    @Scheduled(fixedDelay = 200)
    public void dispatch() {
        if (!workerPermits.tryAcquire()) return;

        String clientId = roundRobinUsers.poll();
        if (clientId == null) {
            workerPermits.release();
            return;
        }

        ConcurrentLinkedQueue<String> q = userQueues.get(clientId);
        String ticketId = (q != null) ? q.poll() : null;

        if (ticketId == null) {
            usersInRoundRobin.remove(clientId);
            workerPermits.release();
            return;
        }

        // If this client still has queued jobs, put it back into round-robin.
        if (q != null && !q.isEmpty()) {
            roundRobinUsers.add(clientId);
        } else {
            usersInRoundRobin.remove(clientId);
        }

        OcrTicketState current = tickets.get(ticketId);
        if (current != null) {
            tickets.put(ticketId, current.withState(OcrTicketState.Status.PROCESSING, null, null));
        }

        qlog("DISPATCH", clientId, ticketId,
                "remainingUserQueue=" + (q == null ? 0 : q.size()) +
                        " permitsLeftAfterAcquire=" + workerPermits.availablePermits());

        executor.submit(() -> {
            try {
                processOne(ticketId);
            } finally {
                workerPermits.release();
            }
        });
    }

    // Processes one OCR ticket by calling the OCR service.
    private void processOne(String ticketId) {
        OcrJob job = ticketJobs.get(ticketId);

        if (job == null) {
            OcrTicketState st = tickets.get(ticketId);
            if (st != null) {
                tickets.put(ticketId, st.withState(
                        OcrTicketState.Status.FAILED,
                        null,
                        "OCR job bulunamadı."
                ));
            }
            return;
        }

        OcrTicketState initial = tickets.get(ticketId);
        String owner = initial != null ? initial.getOwnerClientId() : "?";

        try {
            qlog("PROCESS_START", owner, ticketId,
                    "imageBase64Len=" + (job.imageBase64() == null ? 0 : job.imageBase64().length()));

            // Rate limits outgoing OCR model calls.
            synchronized (rateLock) {
                long now = System.currentTimeMillis();
                long wait = MIN_INTERVAL_MS - (now - lastCallMs);

                if (wait > 0) {
                    Thread.sleep(wait);
                }

                lastCallMs = System.currentTimeMillis();
            }

            OcrLayoutResponse result = documentOcrService.analyzeDocumentLayout(
                    job.imageBase64(),
                    job.mimeType()
            );

            OcrTicketState st = tickets.get(ticketId);
            if (st != null) {
                tickets.put(ticketId, st.withState(
                        OcrTicketState.Status.COMPLETED,
                        result,
                        null
                ));
            }

            qlog("PROCESS_DONE", owner, ticketId,
                    "status=COMPLETED success=" + (result != null && result.isSuccess()) +
                            " errorCode=" + (result != null ? result.getErrorCode() : null));

        } catch (Exception e) {
            OcrTicketState st = tickets.get(ticketId);
            if (st != null) {
                tickets.put(ticketId, st.withState(
                        OcrTicketState.Status.FAILED,
                        null,
                        "OCR queue işlenirken hata oluştu: " + e.getClass().getSimpleName()
                ));
            }

            qlog("PROCESS_DONE", owner, ticketId,
                    "status=FAILED error=" + e.getClass().getSimpleName());

        } finally {
            // Removes the heavy Base64 job payload after processing.
            ticketJobs.remove(ticketId);
        }
    }

    // Periodically removes expired tickets and orphan job data.
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void cleanup() {
        long now = System.currentTimeMillis();

        tickets.entrySet().removeIf(e ->
                now - e.getValue().getCreatedAtMs() > TTL_MS
        );

        ticketJobs.keySet().removeIf(id -> !tickets.containsKey(id));
    }
}