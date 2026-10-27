package br.com.desenvolvedorgustavolopes.moneyPilot.auth;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.TooManyAttemptsException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contador de tentativas em memória, por chave. A janela começa no primeiro evento e expira após a
 * duração. Os contadores zeram no restart do app (uma instância só).
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class AttemptLimiter {

    private static final int CLEANUP_EVERY = 100;

    private record Window(Instant start, int count, Duration duration) {
        boolean expiredAt(Instant now) {
            return !now.isBefore(start.plus(duration));
        }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private int calls;

    public AttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Lança {@link TooManyAttemptsException} se a chave já atingiu o limite na janela atual. */
    public void check(String key, int limit) {
        cleanupSometimes();
        Instant now = clock.instant();
        Window window = windows.get(key);

        if (window != null && !window.expiredAt(now) && window.count() >= limit) {
            throw new TooManyAttemptsException(Duration.between(now, window.start().plus(window.duration())));
        }
    }

    public void record(String key, Duration duration) {
        Instant now = clock.instant();
        windows.compute(key, (k, current) -> current == null || current.expiredAt(now)
                ? new Window(now, 1, duration)
                : new Window(current.start(), current.count() + 1, current.duration()));
    }

    public void reset(String key) {
        windows.remove(key);
    }

    private synchronized void cleanupSometimes() {
        if (++calls % CLEANUP_EVERY != 0) {
            return;
        }
        Instant now = clock.instant();
        windows.entrySet().removeIf(entry -> entry.getValue().expiredAt(now));
    }
}
