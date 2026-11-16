package fr.legrandcinema.auth;

import fr.legrandcinema.shared.ApiException;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/** Local abuse guard. Production multi-instance deployments also require an edge rate limit. */
@Component
public class LoginThrottle {
    private record Window(long start, int count) {}

    private final Map<String, Window> windows = new LinkedHashMap<>();
    private final Clock clock;

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public synchronized void check(String key) {
        long now = clock.millis();
        Window w = windows.get(key);
        if (w == null || now - w.start() > 60000) w = new Window(now, 0);
        if (w.count() >= 20)
            throw new ApiException(429, "Trop de tentatives. Réessayez dans une minute.");
        if (windows.size() > 10000) windows.remove(windows.keySet().iterator().next());
        windows.put(key, new Window(w.start(), w.count() + 1));
    }
}
