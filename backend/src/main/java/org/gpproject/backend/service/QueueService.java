package org.gpproject.backend.service;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@EnableScheduling
public class QueueService {

    private final GeminiService geminiService;

    // ✅ Ticket store (owner + status + payload)
    private final ConcurrentHashMap<String, TicketState> tickets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> ticketPrompts = new ConcurrentHashMap<>();

    // ✅ Per-user queues (fairness)
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> userQueues = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> roundRobinUsers = new ConcurrentLinkedQueue<>();
    private final Set<String> usersInRoundRobin = ConcurrentHashMap.newKeySet();

    // ✅ Worker pool (aynı anda kaç Gemini çağrısı)
    private static final int MAX_CONCURRENT_WORKERS = 4; // ihtiyaca göre 1-4
    private final Semaphore workerPermits = new Semaphore(MAX_CONCURRENT_WORKERS);

    private final ExecutorService executor = Executors.newFixedThreadPool(
            MAX_CONCURRENT_WORKERS,
            new ThreadFactory() {
                private final AtomicInteger i = new AtomicInteger(1);
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "gemini-worker-" + i.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            }
    );

    // ✅ Rate limit (global). Gemini kota/rate için.
    // Örn: 1200ms => ~0.8 req/s. (Senin eski hal 5000ms idi.)
    private static final long MIN_INTERVAL_MS = 300;
    private final Object rateLock = new Object();
    private long lastCallMs = 0;

    // ✅ TTL cleanup (RAM şişmesin)
    private static final long TTL_MS = 2L * 60L * 60L * 1000L; // 2 saat

    public QueueService(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    // Producer
    public String addToQueue(String clientId, String promptText) {

        userQueues.putIfAbsent(clientId, new ConcurrentLinkedQueue<>());
        ConcurrentLinkedQueue<String> q = userQueues.get(clientId);

        String ticketId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        tickets.put(ticketId, new TicketState(clientId, TicketState.Status.QUEUED, null, now));
        ticketPrompts.put(ticketId, promptText);

        q.add(ticketId);

        // round robin listesine user ekle (bir kere)
        if (usersInRoundRobin.add(clientId)) {
            roundRobinUsers.add(clientId);
        }

        return ticketId;
    }

    // ✅ Owner check (çok kullanıcı için kritik)
    public TicketState getTicket(String clientId, String ticketId) {
        TicketState st = tickets.get(ticketId);
        if (st == null) return null;

        // anonymous kullanan eski clientlar için: owner kontrolü gevşek olabilir.
        // Gerçek multi-user için Android mutlaka X-Client-Id göndermeli.
        if (!"anonymous".equals(clientId) && !st.getOwnerClientId().equals(clientId)) {
            return null; // başkası görmesin
        }
        return st;
    }

    // Dispatcher: sık çalışır, permit varsa iş dağıtır
    @Scheduled(fixedDelay = 200)
    public void dispatch() {
        // permit yoksa çık
        if (!workerPermits.tryAcquire()) return;

        String clientId = roundRobinUsers.poll();
        if (clientId == null) {
            workerPermits.release();
            return;
        }

        ConcurrentLinkedQueue<String> q = userQueues.get(clientId);
        String ticketId = (q != null) ? q.poll() : null;

        // bu user’ın kuyruğu boşsa round robin’den düş
        if (ticketId == null) {
            usersInRoundRobin.remove(clientId);
            workerPermits.release();
            return;
        }

        // kuyruğunda hâlâ iş varsa user’ı sona ekle (round robin)
        if (q != null && !q.isEmpty()) {
            roundRobinUsers.add(clientId);
        } else {
            usersInRoundRobin.remove(clientId);
        }

        // işi worker’a ver
        TicketState current = tickets.get(ticketId);
        if (current != null) {
            tickets.put(ticketId, current.withStatus(TicketState.Status.PROCESSING, null));
        }

        executor.submit(() -> {
            try {
                processOne(ticketId);
            } finally {
                workerPermits.release();
            }
        });
    }

    private void processOne(String ticketId) {
        String prompt = ticketPrompts.get(ticketId);
        if (prompt == null) {
            TicketState st = tickets.get(ticketId);
            if (st != null) tickets.put(ticketId, st.withStatus(TicketState.Status.FAILED, null));
            return;
        }

        try {
            // ✅ global rate-limit
            synchronized (rateLock) {
                long now = System.currentTimeMillis();
                long wait = MIN_INTERVAL_MS - (now - lastCallMs);
                if (wait > 0) Thread.sleep(wait);
                lastCallMs = System.currentTimeMillis();
            }

            String payload = geminiService.callGemini(prompt);
            TicketState st = tickets.get(ticketId);
            if (st != null) tickets.put(ticketId, st.withStatus(TicketState.Status.COMPLETED, payload));

        } catch (Exception e) {
            TicketState st = tickets.get(ticketId);
            if (st != null) tickets.put(ticketId, st.withStatus(TicketState.Status.FAILED, null));
        } finally {
            ticketPrompts.remove(ticketId);
        }
    }

    // TTL cleanup
    @Scheduled(fixedDelay = 10 * 60 * 1000) // 10 dk
    public void cleanup() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> now - e.getValue().getCreatedAtMs() > TTL_MS);
        ticketPrompts.keySet().removeIf(id -> !tickets.containsKey(id));
    }
}
