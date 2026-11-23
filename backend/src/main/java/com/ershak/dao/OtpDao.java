package com.ershak.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** One-time codes. Only the SHA-256 of the code is stored, never the code itself. */
public class OtpDao {

    public record OtpRow(long transactionId, String codeHash, long expiresAt, int attempts) {
    }

    public void upsert(Connection c, long transactionId, String codeHash, long expiresAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO otp_codes "
                + "(transaction_id, code_hash, expires_at, attempts) VALUES (?,?,?,0)")) {
            ps.setLong(1, transactionId);
            ps.setString(2, codeHash);
            ps.setLong(3, expiresAt);
            ps.executeUpdate();
        }
    }

    public Optional<OtpRow> find(Connection c, long transactionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM otp_codes WHERE transaction_id = ?")) {
            ps.setLong(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OtpRow(rs.getLong("transaction_id"), rs.getString("code_hash"),
                        rs.getLong("expires_at"), rs.getInt("attempts")));
            }
        }
    }

    public void incrementAttempts(Connection c, long transactionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE otp_codes SET attempts = attempts + 1 WHERE transaction_id = ?")) {
            ps.setLong(1, transactionId);
            ps.executeUpdate();
        }
    }

    public void delete(Connection c, long transactionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM otp_codes WHERE transaction_id = ?")) {
            ps.setLong(1, transactionId);
            ps.executeUpdate();
        }
    }
}
