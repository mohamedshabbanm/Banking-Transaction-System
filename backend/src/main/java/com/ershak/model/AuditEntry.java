package com.ershak.model;

/**
 * One line in the tamper-evident audit log.
 * {@code hash = SHA-256(previousHash | timestamp | actor | action | details)}
 */
public record AuditEntry(long id, long timestamp, String actor, String action, String details,
                         String previousHash, String hash) {
}
