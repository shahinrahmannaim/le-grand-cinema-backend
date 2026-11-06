package fr.legrandcinema.booking;

import jakarta.annotation.PreDestroy;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@EnableScheduling
public class SeatEvents {
    private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    public SseEmitter connect(UUID screening) {
        SseEmitter emitter = new SseEmitter(120000L);
        streams.computeIfAbsent(screening, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        Runnable remove =
                () ->
                        streams.computeIfPresent(
                                screening,
                                (k, set) -> {
                                    set.remove(emitter);
                                    return set.isEmpty() ? null : set;
                                });
        emitter.onCompletion(remove);
        emitter.onTimeout(
                () -> {
                    remove.run();
                    emitter.complete();
                });
        emitter.onError(e -> remove.run());
        send(emitter, "refresh", screening.toString());
        return emitter;
    }

    public void changed(UUID screening) {
        Set<SseEmitter> set = streams.get(screening);
        if (set != null) set.forEach(e -> send(e, "refresh", screening.toString()));
    }

    @Scheduled(fixedDelay = 20000)
    void heartbeat() {
        streams.values().forEach(set -> set.forEach(e -> send(e, "heartbeat", "alive")));
    }

    private void send(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException e) {
            emitter.complete();
        }
    }

    @PreDestroy
    public void shutdown() {
        streams.values().forEach(set -> set.forEach(SseEmitter::complete));
        streams.clear();
    }
}
