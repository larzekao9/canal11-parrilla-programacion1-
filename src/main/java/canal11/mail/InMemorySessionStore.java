package canal11.mail;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Sesiones en memoria (para tests o si no se necesita persistir entre reinicios). */
public class InMemorySessionStore implements SessionStore {

    private final Map<Integer, Instant> expira = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemorySessionStore() { this(Clock.systemUTC()); }

    public InMemorySessionStore(Clock clock) { this.clock = clock; }

    @Override
    public boolean isActive(int userId) {
        Instant e = expira.get(userId);
        return e != null && e.isAfter(clock.instant());
    }

    @Override
    public void open(int userId, int minutes) {
        expira.put(userId, clock.instant().plusSeconds(minutes * 60L));
    }
}
