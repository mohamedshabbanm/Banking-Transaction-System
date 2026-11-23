package com.ershak.dao;

import com.ershak.model.SavingsGoal;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data access for savings goals. */
public class GoalDao {

    public long insert(Connection c, SavingsGoal g) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO savings_goals (user_id, account_id, name, "
                + "target_amount, saved_amount, target_date, status, created_at) VALUES (?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, g.getUserId());
            ps.setLong(2, g.getAccountId());
            ps.setString(3, g.getName());
            ps.setString(4, g.getTargetAmount().toPlainString());
            ps.setString(5, g.getSavedAmount().toPlainString());
            ps.setString(6, g.getTargetDate());
            ps.setString(7, g.getStatus().name());
            ps.setLong(8, g.getCreatedAt());
            ps.executeUpdate();
            long id = Jdbc.lastId(ps);
            g.setId(id);
            return id;
        }
    }

    public void update(Connection c, SavingsGoal g) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE savings_goals SET saved_amount = ?, status = ? WHERE id = ?")) {
            ps.setString(1, g.getSavedAmount().toPlainString());
            ps.setString(2, g.getStatus().name());
            ps.setLong(3, g.getId());
            ps.executeUpdate();
        }
    }

    public void delete(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM savings_goals WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public Optional<SavingsGoal> findById(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM savings_goals WHERE id = ?")) {
            ps.setLong(1, id);
            List<SavingsGoal> l = list(ps);
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
    }

    public List<SavingsGoal> findByUser(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM savings_goals WHERE user_id = ? ORDER BY status, created_at DESC")) {
            ps.setLong(1, userId);
            return list(ps);
        }
    }

    private List<SavingsGoal> list(PreparedStatement ps) throws SQLException {
        List<SavingsGoal> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                SavingsGoal g = new SavingsGoal();
                g.setId(rs.getLong("id"));
                g.setUserId(rs.getLong("user_id"));
                g.setAccountId(rs.getLong("account_id"));
                g.setName(rs.getString("name"));
                g.setTargetAmount(new BigDecimal(rs.getString("target_amount")));
                g.setSavedAmount(new BigDecimal(rs.getString("saved_amount")));
                g.setTargetDate(rs.getString("target_date"));
                g.setStatus(SavingsGoal.Status.valueOf(rs.getString("status")));
                g.setCreatedAt(rs.getLong("created_at"));
                out.add(g);
            }
        }
        return out;
    }
}
