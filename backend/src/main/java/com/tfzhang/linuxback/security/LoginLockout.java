package com.tfzhang.linuxback.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consecutive login failures per account string. In-memory only: two JVM
 * processes do not share this counter.
 */
@Component
public class LoginLockout {

    public static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, FailureState> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginLockout() {
        this(Clock.systemUTC());
    }

    LoginLockout(Clock clock) {
        this.clock = clock;
    }

    public boolean isLocked(String userAccount) {
        FailureState state = attempts.get(userAccount);
        if (state == null || state.lockedUntil() == null) {
            return false;
        }
        if (clock.instant().isBefore(state.lockedUntil())) {
            return true;
        }
        attempts.remove(userAccount, state);
        return false;
    }

    public void recordFailure(String userAccount) {
        attempts.compute(userAccount, (key, state) -> {
            Instant now = clock.instant();
            if (state != null && state.lockedUntil() != null && now.isBefore(state.lockedUntil())) {
                return state;
            }
            int next = (state == null ? 0 : state.failures()) + 1;
            Instant lockUntil = next >= MAX_FAILURES ? now.plus(LOCK_DURATION) : null;
            return new FailureState(next, lockUntil);
        });
    }

    public void clear(String userAccount) {
        attempts.remove(userAccount);
    }

    private record FailureState(int failures, Instant lockedUntil) {
    }
}
