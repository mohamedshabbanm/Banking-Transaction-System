package com.ershak.dao;

import com.ershak.fraud.RuleResult;
import com.ershak.model.FraudEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Stores the Fraud Guard's verdict for each evaluated transaction. */
public class FraudDao {

    public void insert(Connection c, long transactionId, int score, List<RuleResult> rules, String decision,
                       long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO fraud_events "
                + "(transaction_id, score, rules, decision, created_at) VALUES (?,?,?,?,?)")) {
            ps.setLong(1, transactionId);
            ps.setInt(2, score);
            ps.setString(3, String.join("\n", rules.stream().map(RuleResult::encode).toList()));
            ps.setString(4, decision);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    public void markReviewed(Connection c, long transactionId, String decision, long reviewer, long now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE fraud_events SET decision = ?, reviewed_by = ?, reviewed_at = ? WHERE transaction_id = ?")) {
            ps.setString(1, decision);
            ps.setLong(2, reviewer);
            ps.setLong(3, now);
            ps.setLong(4, transactionId);
            ps.executeUpdate();
        }
    }

    public Optional<FraudEvent> findByTransaction(Connection c, long transactionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM fraud_events WHERE transaction_id = ?")) {
            ps.setLong(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public int countSince(Connection c, long sinceMillis) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM fraud_events WHERE created_at >= ?")) {
            ps.setLong(1, sinceMillis);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private FraudEvent map(ResultSet rs) throws SQLException {
        String raw = rs.getString("rules");
        List<RuleResult> rules = new ArrayList<>();
        if (raw != null && !raw.isBlank()) {
            Arrays.stream(raw.split("\n")).map(RuleResult::decode).forEach(rules::add);
        }
        return new FraudEvent(rs.getLong("id"), rs.getLong("transaction_id"), rs.getInt("score"), rules,
                rs.getString("decision"), Jdbc.getNullableLong(rs, "reviewed_by"),
                Jdbc.getNullableLong(rs, "reviewed_at"), rs.getLong("created_at"));
    }
}
