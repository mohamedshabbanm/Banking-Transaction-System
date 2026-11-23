package com.ershak.security;

import com.ershak.exception.AuthenticationException;
import com.ershak.model.User;
import com.ershak.util.BankClock;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * Issues random session tokens and expires them after inactivity.
 * Tokens are 32 random bytes, so they can't be guessed.
 */
public class SessionManager {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final IntSupplier timeoutMinutes;

    public SessionManager(IntSupplier timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public Session create(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Session session = new Session(token, user.getId(), user.getUsername(), user.getRole(), BankClock.nowMillis());
        sessions.put(token, session);
        return session;
    }

    /** Returns the session for a token, refreshing its activity time, or throws 401. */
    public Session validate(String token) {
        return validate(token, true);
    }

    /**
     * Same as above; with touch=false the call does not count as user activity.
     * The web app polls for new notifications this way, so polling never keeps an idle session alive.
     */
    public Session validate(String token, boolean touch) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationException("NOT_LOGGED_IN", "Log in to continue.");
        }
        Session session = sessions.get(token);
        if (session == null) {
            throw new AuthenticationException("NOT_LOGGED_IN", "Log in to continue.");
        }
        long now = BankClock.nowMillis();
        long timeoutMillis = timeoutMinutes.getAsInt() * 60_000L;
        if (now - session.lastSeen() > timeoutMillis) {
            sessions.remove(token);
            throw new AuthenticationException("SESSION_EXPIRED", "Your session expired. Log in again.");
        }
        if (touch) {
            session.touch(now);
        }
        return session;
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** Logs a user out everywhere (used when their password changes or an admin locks them). */
    public void invalidateUser(long userId) {
        sessions.values().removeIf(s -> s.userId() == userId);
    }
}
