package com.example.banking.monitor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory lightweight event bus: keeps a bounded history ring and
 * fans out to all connected SSE subscribers. Non-blocking for banking ops:
 * a slow/disconnected emitter never blocks the request thread.
 */
@Component
public class FlowEventBus {
    private static final int MAX_HISTORY = 300;
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Deque<FlowEvent> history = new ConcurrentLinkedDeque<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public void publish(FlowEvent event) {
        if (event == null) return;
        if (history.size() >= MAX_HISTORY) {
            history.pollFirst();
        }
        history.addLast(event);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("flow")
                        .data(event));
            } catch (Exception ex) {
                emitters.remove(emitter);
                try { emitter.completeWithError(ex); } catch (Exception ignored) { /* noop */ }
            }
        }
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((t) -> emitters.remove(emitter));
        emitters.add(emitter);
        // Replay the latest event so a fresh subscriber immediately sees context.
        FlowEvent latest = history.peekLast();
        if (latest != null) {
            try {
                emitter.send(SseEmitter.event().name("flow").data(latest));
            } catch (Exception ex) {
                emitters.remove(emitter);
            }
        }
        return emitter;
    }

    public List<FlowEvent> recent(int limit) {
        int n = Math.min(Math.max(limit, 1), MAX_HISTORY);
        return history.stream().skip(Math.max(0, history.size() - n)).toList();
    }

    public void clear() {
        history.clear();
    }

    public int subscriberCount() {
        return emitters.size();
    }
}
