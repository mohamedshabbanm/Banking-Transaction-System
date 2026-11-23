package com.ershak.security;

import com.ershak.model.Role;

/** A logged-in user's session. Kept in memory on the server; the client only holds the random token. */
public final class Session {

    private final String token;
    private final long userId;
    private final String username;
    private final Role role;
    private volatile long lastSeen;

    public Session(String token, long userId, String username, Role role, long now) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.lastSeen = now;
    }

    public void touch(long now) {
        lastSeen = now;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public String token() { return token; }
    public long userId() { return userId; }
    public String username() { return username; }
    public Role role() { return role; }
    public long lastSeen() { return lastSeen; }
}
