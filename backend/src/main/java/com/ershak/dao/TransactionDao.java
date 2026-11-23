package com.ershak.dao;

import com.ershak.model.Transaction;
import com.ershak.model.TransactionStatus;
import com.ershak.model.TransactionType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data access for transactions. */
public class TransactionDao {

    private static final String COLUMNS = "id, reference, type, from_account_id, to_account_id, amount, fee, "
            + "status, initiated_by, note, created_at, completed_at";

    public long insert(Connection c, Transaction t) throws SQLException {
        String sql = "INSERT INTO transactions (reference, type, from_account_id, to_account_id, amount, fee, status, "
                + "initiated_by, note, created_at, completed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, t.getReference());
            ps.setString(2, t.getType().name());
            Jdbc.setNullableLong(ps, 3, t.getFromAccountId());
            Jdbc.setNullableLong(ps, 4, t.getToAccountId());
            ps.setString(5, t.getAmount().toPlainString());
            ps.setString(6, t.getFee().toPlainString());
            ps.setString(7, t.getStatus().name());
            ps.setLong(8, t.getInitiatedBy());
            ps.setString(9, t.getNote());
            ps.setLong(10, t.getCreatedAt());
            Jdbc.setNullableLong(ps, 11, t.getCompletedAt());
            ps.executeUpdate();
            long id = Jdbc.lastId(ps);
            t.setId(id);
            return id;
        }
    }

    public void updateStatus(Connection c, Transaction t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE transactions SET status = ?, completed_at = ? WHERE id = ?")) {
            ps.setString(1, t.getStatus().name());
            Jdbc.setNullableLong(ps, 2, t.getCompletedAt());
            ps.setLong(3, t.getId());
            ps.executeUpdate();
        }
    }

    public Optional<Transaction> findById(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM transactions WHERE id = ?")) {
            ps.setLong(1, id);
            List<Transaction> l = list(ps);
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
    }

    /** How many references already exist with this prefix (used to number today's transactions). */
    public int countReferencesWithPrefix(Connection c, String prefix) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM transactions WHERE reference LIKE ?")) {
            ps.setString(1, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Every transaction touching an account (sent or received), newest first, within a time range. */
    public List<Transaction> findForAccount(Connection c, long accountId, long fromMillis, long toMillis)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM transactions "
                + "WHERE (from_account_id = ? OR to_account_id = ?) AND created_at >= ? AND created_at < ? "
                + "ORDER BY created_at DESC, id DESC")) {
            ps.setLong(1, accountId);
            ps.setLong(2, accountId);
            ps.setLong(3, fromMillis);
            ps.setLong(4, toMillis);
            return list(ps);
        }
    }

    /** Recent transactions across all of a user's accounts, newest first. */
    public List<Transaction> findRecentForUser(Connection c, long userId, int limit) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM transactions t "
                + "WHERE t.from_account_id IN (SELECT id FROM accounts WHERE user_id = ?) "
                + "OR t.to_account_id IN (SELECT id FROM accounts WHERE user_id = ?) "
                + "ORDER BY created_at DESC, id DESC LIMIT ?")) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setInt(3, limit);
            return list(ps);
        }
    }

    /**
     * Outgoing (withdraw / transfer) transactions sent by a user since a time,
     * optionally only of one type and only with the given statuses.
     */
    public List<Transaction> findOutgoingByUser(Connection c, long userId, long sinceMillis,
                                                TransactionType onlyType, List<TransactionStatus> statuses)
            throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM transactions "
                + "WHERE initiated_by = ? AND created_at >= ? AND type IN ('WITHDRAW','TRANSFER')");
        if (onlyType != null) {
            sql.append(" AND type = ?");
        }
        sql.append(" AND status IN (");
        for (int i = 0; i < statuses.size(); i++) {
            sql.append(i == 0 ? "?" : ",?");
        }
        sql.append(") ORDER BY created_at DESC");
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            ps.setLong(i++, userId);
            ps.setLong(i++, sinceMillis);
            if (onlyType != null) {
                ps.setString(i++, onlyType.name());
            }
            for (TransactionStatus s : statuses) {
                ps.setString(i++, s.name());
            }
            return list(ps);
        }
    }

    /** Outgoing operations from one account since a time (for the savings monthly limit). */
    public int countOutgoingFromAccount(Connection c, long accountId, long sinceMillis) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM transactions WHERE from_account_id = ? "
                + "AND type IN ('WITHDRAW','TRANSFER') AND status IN ('COMPLETED','PENDING_OTP','PENDING_REVIEW') "
                + "AND created_at >= ?")) {
            ps.setLong(1, accountId);
            ps.setLong(2, sinceMillis);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** True when the user has already completed a transfer to this account before. */
    public boolean hasCompletedTransferTo(Connection c, long userId, long toAccountId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM transactions WHERE initiated_by = ? "
                + "AND to_account_id = ? AND type = 'TRANSFER' AND status = 'COMPLETED' LIMIT 1")) {
            ps.setLong(1, userId);
            ps.setLong(2, toAccountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<Transaction> findByStatus(Connection c, TransactionStatus status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM transactions WHERE status = ? ORDER BY created_at DESC")) {
            ps.setString(1, status.name());
            return list(ps);
        }
    }

    public int countSince(Connection c, long sinceMillis) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM transactions WHERE created_at >= ?")) {
            ps.setLong(1, sinceMillis);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private List<Transaction> list(PreparedStatement ps) throws SQLException {
        List<Transaction> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Transaction t = new Transaction();
                t.setId(rs.getLong("id"));
                t.setReference(rs.getString("reference"));
                t.setType(TransactionType.valueOf(rs.getString("type")));
                t.setFromAccountId(Jdbc.getNullableLong(rs, "from_account_id"));
                t.setToAccountId(Jdbc.getNullableLong(rs, "to_account_id"));
                t.setAmount(new BigDecimal(rs.getString("amount")));
                t.setFee(new BigDecimal(rs.getString("fee")));
                t.setStatus(TransactionStatus.valueOf(rs.getString("status")));
                t.setInitiatedBy(rs.getLong("initiated_by"));
                t.setNote(rs.getString("note"));
                t.setCreatedAt(rs.getLong("created_at"));
                t.setCompletedAt(Jdbc.getNullableLong(rs, "completed_at"));
                out.add(t);
            }
        }
        return out;
    }
}
