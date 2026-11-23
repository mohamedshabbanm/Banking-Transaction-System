package com.ershak.service;

import com.ershak.db.Database;
import com.ershak.model.AuditEntry;
import com.ershak.security.Hashing;
import com.ershak.util.BankClock;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * "El Dafater": the tamper-evident audit log.
 * <p>
 * Each entry stores the hash of the entry before it. If anybody edits or deletes a row
 * directly in the database, the chain no longer matches and {@link #verify()} reports where it broke.
 */
public class AuditService {

    public static final String GENESIS = "GENESIS";

    public record VerifyResult(boolean intact, int totalEntries, Long firstBrokenId, String message) {
    }

    private final Database db;
    private final Repositories repos;

    public AuditService(Database db, Repositories repos) {
        this.db = db;
        this.repos = repos;
    }

    /** Appends an entry inside the caller's transaction, so it commits or rolls back with the business change. */
    public void append(Connection c, String actor, String action, String details) throws SQLException {
        long ts = BankClock.nowMillis();
        String previous = repos.audit().lastHash(c).orElse(GENESIS);
        String hash = computeHash(previous, ts, actor, action, details);
        repos.audit().insert(c, ts, actor, action, details, previous, hash);
    }

    /** The username for audit lines (falls back to the id if the user is gone). */
    public String actor(Connection c, long userId) throws SQLException {
        return repos.users().findById(c, userId).map(u -> u.getUsername()).orElse("user:" + userId);
    }

    public static String computeHash(String previousHash, long ts, String actor, String action, String details) {
        return Hashing.sha256(previousHash + "|" + ts + "|" + actor + "|" + action + "|" + details);
    }

    /** Recomputes the whole chain and returns the first entry that doesn't match. */
    public VerifyResult verify() {
        List<AuditEntry> entries = db.read(c -> repos.audit().findAllAscending(c));
        String expectedPrevious = GENESIS;
        for (AuditEntry e : entries) {
            String recomputed = computeHash(e.previousHash(), e.timestamp(), e.actor(), e.action(), e.details());
            if (!e.previousHash().equals(expectedPrevious) || !recomputed.equals(e.hash())) {
                return new VerifyResult(false, entries.size(), e.id(),
                        "Entry #" + e.id() + " was changed or a record before it was removed.");
            }
            expectedPrevious = e.hash();
        }
        return new VerifyResult(true, entries.size(), null, "Chain intact. Nobody changed the records.");
    }

    public List<AuditEntry> recent(int limit) {
        return db.read(c -> repos.audit().findRecent(c, limit));
    }
}
