package org.gpproject.backend.service.text;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class QueueService {

    private final GeminiService geminiService;

    // Stores ticket status and final payload by ticket ID.
    private final ConcurrentHashMap<String, TicketState> tickets = new ConcurrentHashMap<>();

    // Stores the original user prompt by ticket ID until the job is processed.
    private final ConcurrentHashMap<String, String> ticketPrompts = new ConcurrentHashMap<>();

    // Keeps a separate queue for each client to provide fair scheduling.
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> userQueues = new ConcurrentHashMap<>();

    // Stores client IDs waiting for processing in round-robin order.
    private final ConcurrentLinkedQueue<String> roundRobinUsers = new ConcurrentLinkedQueue<>();

    // Prevents adding the same client multiple times to the round-robin queue.
    private final Set<String> usersInRoundRobin = ConcurrentHashMap.newKeySet();

    // Limits how many Gemini text generation jobs can run at the same time.
    private static final int MAX_CONCURRENT_WORKERS = 2;
    private final Semaphore workerPermits = new Semaphore(MAX_CONCURRENT_WORKERS);

    // Worker pool used to process Gemini jobs in background threads.
    private final ExecutorService executor = Executors.newFixedThreadPool(
            MAX_CONCURRENT_WORKERS,
            new ThreadFactory() {
                private final AtomicInteger i = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "gemini-worker-" + i.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            }
    );

    // Global minimum interval between Gemini API calls.
    private static final long MIN_INTERVAL_MS = 1200;
    private final Object rateLock = new Object();
    private long lastCallMs = 0;

    // Ticket lifetime before it is removed from memory.
    private static final long TTL_MS = 2L * 60L * 60L * 1000L;

    public QueueService(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    // Adds a new prompt request to the client's queue and returns a ticket ID.
    public String addToQueue(String clientId, String promptText) {
        userQueues.putIfAbsent(clientId, new ConcurrentLinkedQueue<>());
        ConcurrentLinkedQueue<String> q = userQueues.get(clientId);

        String ticketId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        tickets.put(
                ticketId,
                new TicketState(clientId, TicketState.Status.QUEUED, null, now)
        );

        ticketPrompts.put(ticketId, promptText);
        q.add(ticketId);

        // Add this client to round-robin scheduling only once.
        if (usersInRoundRobin.add(clientId)) {
            roundRobinUsers.add(clientId);
        }

        return ticketId;
    }

    // Returns a ticket only if it belongs to the requesting client.
    public TicketState getTicket(String clientId, String ticketId) {
        TicketState st = tickets.get(ticketId);

        if (st == null) {
            return null;
        }

        // For real multi-user behavior, Android should always send X-Client-Id.
        if (!"anonymous".equals(clientId) && !st.getOwnerClientId().equals(clientId)) {
            return null;
        }

        return st;
    }

    // Periodically dispatches queued tickets to available workers.
    @Scheduled(fixedDelay = 200)
    public void dispatch() {
        // If all workers are busy, do not start another job.
        if (!workerPermits.tryAcquire()) {
            return;
        }

        String clientId = roundRobinUsers.poll();

        if (clientId == null) {
            workerPermits.release();
            return;
        }

        ConcurrentLinkedQueue<String> q = userQueues.get(clientId);
        String ticketId = (q != null) ? q.poll() : null;

        // If this client's queue is empty, remove it from round-robin.
        if (ticketId == null) {
            usersInRoundRobin.remove(clientId);
            workerPermits.release();
            return;
        }

        // If the client still has queued jobs, put it back at the end.
        if (q != null && !q.isEmpty()) {
            roundRobinUsers.add(clientId);
        } else {
            usersInRoundRobin.remove(clientId);
        }

        // Mark this ticket as currently processing.
        TicketState current = tickets.get(ticketId);

        if (current != null) {
            tickets.put(
                    ticketId,
                    current.withStatus(TicketState.Status.PROCESSING, null)
            );
        }

        // Submit the actual Gemini work to the background executor.
        executor.submit(() -> {
            try {
                processOne(ticketId);
            } finally {
                workerPermits.release();
            }
        });
    }

    // Processes one queued Gemini text-generation job.
    private void processOne(String ticketId) {
        String prompt = ticketPrompts.get(ticketId);

        if (prompt == null) {
            TicketState st = tickets.get(ticketId);

            if (st != null) {
                tickets.put(ticketId, st.withStatus(TicketState.Status.FAILED, null));
            }

            return;
        }

        try {
            // Apply global rate limiting before calling Gemini.
            synchronized (rateLock) {
                long now = System.currentTimeMillis();
                long wait = MIN_INTERVAL_MS - (now - lastCallMs);

                if (wait > 0) {
                    Thread.sleep(wait);
                }

                lastCallMs = System.currentTimeMillis();
            }

            String payload = geminiService.callGemini(prompt);

            TicketState st = tickets.get(ticketId);

            if (st != null) {
                tickets.put(
                        ticketId,
                        st.withStatus(TicketState.Status.COMPLETED, payload)
                );
            }

        } catch (Exception e) {
            TicketState st = tickets.get(ticketId);

            if (st != null) {
                tickets.put(ticketId, st.withStatus(TicketState.Status.FAILED, null));
            }

        } finally {
            // Remove the original prompt after the job finishes to reduce memory usage.
            ticketPrompts.remove(ticketId);
        }
    }

    // Periodically removes expired tickets and orphan prompt data.
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void cleanup() {
        long now = System.currentTimeMillis();

        tickets.entrySet().removeIf(e ->
                now - e.getValue().getCreatedAtMs() > TTL_MS
        );

        ticketPrompts.keySet().removeIf(id ->
                !tickets.containsKey(id)
        );
    }
}