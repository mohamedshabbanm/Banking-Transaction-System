package com.ershak.dao;

import com.ershak.model.AuditEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Append-only access to the audit log. There is deliberately no update or delete method here. */
public class AuditDao {

    public void insert(Connection c, long ts, String actor, String action, String details,
                       String previousHash, String hash) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO audit_log "
                + "(ts, actor, action, details, previous_hash, hash) VALUES (?,?,?,?,?,?)")) {
            ps.setLong(1, ts);
            ps.setString(2, actor);
            ps.setString(3, action);
            ps.setString(4, details);
            ps.setString(5, previousHash);
            ps.setString(6, hash);
            ps.executeUpdate();
        }
    }

    public Optional<String> lastHash(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT hash FROM audit_log ORDER BY id DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
        }
    }

    /** The whole log in insertion order (for integrity verification). */
    public List<AuditEntry> findAllAscending(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM audit_log ORDER BY id ASC")) {
            return list(ps);
        }
    }

    public List<AuditEntry> findRecent(Connection c, int limit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM audit_log ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            return list(ps);
        }
    }

    public int count(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM audit_log");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private List<AuditEntry> list(PreparedStatement ps) throws SQLException {
        List<AuditEntry> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new AuditEntry(rs.getLong("id"), rs.getLong("ts"), rs.getString("actor"),
                        rs.getString("action"), rs.getString("details"), rs.getString("previous_hash"),
                        rs.getString("hash")));
            }
        }
        return out;
    }
}
