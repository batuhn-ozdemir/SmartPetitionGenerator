package org.gpproject.backend.service;

import org.gpproject.backend.model.OcrLayoutResponse;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@EnableScheduling
public class OcrQueueService {

    private record OcrJob(String imageBase64, String mimeType) {}

    private final DocumentOcrService documentOcrService;

    private final ConcurrentHashMap<String, OcrTicketState> tickets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OcrJob> ticketJobs = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> userQueues = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> roundRobinUsers = new ConcurrentLinkedQueue<>();
    private final Set<String> usersInRoundRobin = ConcurrentHashMap.newKeySet();

    private static final int MAX_CONCURRENT_OCR_WORKERS = 2;
    private final Semaphore workerPermits = new Semaphore(MAX_CONCURRENT_OCR_WORKERS);

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

    private static final long MIN_INTERVAL_MS = 1500L;
    private final Object rateLock = new Object();
    private long lastCallMs = 0L;

    private static final long TTL_MS = 2L * 60L * 60L * 1000L;

    public OcrQueueService(DocumentOcrService documentOcrService) {
        this.documentOcrService = documentOcrService;
    }

    private void qlog(String event, String clientId, String ticketId, String extra) {
        System.out.printf(
                "[OCR-QUEUE] event=%s client=%s ticket=%s %s%n",
                event,
                clientId,
                ticketId,
                extra == null ? "" : extra
        );
    }

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

        if (usersInRoundRobin.add(clientId)) {
            roundRobinUsers.add(clientId);
        }

        qlog("ADD", clientId, ticketId,
                "userQueueSize=" + q.size() +
                        " roundRobinUsers=" + roundRobinUsers.size() +
                        " totalTickets=" + tickets.size());

        return ticketId;
    }

    public OcrTicketState getTicket(String clientId, String ticketId) {
        OcrTicketState st = tickets.get(ticketId);
        if (st == null) return null;

        if (!"anonymous".equals(clientId) && !st.getOwnerClientId().equals(clientId)) {
            return null;
        }
        return st;
    }

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

    private void processOne(String ticketId) {
        OcrJob job = ticketJobs.get(ticketId);

        if (job == null) {
            OcrTicketState st = tickets.get(ticketId);
            if (st != null) {
                tickets.put(ticketId, st.withState(OcrTicketState.Status.FAILED, null, "OCR job bulunamadı."));
            }
            return;
        }

        OcrTicketState initial = tickets.get(ticketId);
        String owner = initial != null ? initial.getOwnerClientId() : "?";

        try {
            qlog("PROCESS_START", owner, ticketId,
                    "imageBase64Len=" + (job.imageBase64() == null ? 0 : job.imageBase64().length()));

            synchronized (rateLock) {
                long now = System.currentTimeMillis();
                long wait = MIN_INTERVAL_MS - (now - lastCallMs);
                if (wait > 0) Thread.sleep(wait);
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
            ticketJobs.remove(ticketId);
        }
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> now - e.getValue().getCreatedAtMs() > TTL_MS);
        ticketJobs.keySet().removeIf(id -> !tickets.containsKey(id));
    }
}