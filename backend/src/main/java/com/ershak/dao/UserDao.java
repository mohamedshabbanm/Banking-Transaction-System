package com.ershak.dao;

import com.ershak.model.Role;
import com.ershak.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data access for the users table. */
public class UserDao {

    private static final String COLUMNS = "id, full_name, email, phone, username, password_hash, salt, role, "
            + "failed_attempts, locked_until, must_change_password, created_at";

    public long insert(Connection c, User u) throws SQLException {
        String sql = "INSERT INTO users (full_name, email, phone, username, password_hash, salt, role, "
                + "failed_attempts, locked_until, must_change_password, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getFullName());
            ps.setString(2, u.getEmail());
            ps.setString(3, u.getPhone());
            ps.setString(4, u.getUsername());
            ps.setString(5, u.getPasswordHash());
            ps.setString(6, u.getSalt());
            ps.setString(7, u.getRole().name());
            ps.setInt(8, u.getFailedAttempts());
            ps.setLong(9, u.getLockedUntil());
            ps.setInt(10, u.isMustChangePassword() ? 1 : 0);
            ps.setLong(11, u.getCreatedAt());
            ps.executeUpdate();
            long id = Jdbc.lastId(ps);
            u.setId(id);
            return id;
        }
    }

    public Optional<User> findById(Connection c, long id) throws SQLException {
        return findOne(c, "SELECT " + COLUMNS + " FROM users WHERE id = ?", id);
    }

    public Optional<User> findByUsername(Connection c, String username) throws SQLException {
        return findOne(c, "SELECT " + COLUMNS + " FROM users WHERE username = ?", username);
    }

    public boolean existsByUsername(Connection c, String username) throws SQLException {
        return exists(c, "SELECT 1 FROM users WHERE username = ?", username);
    }

    public boolean existsByEmail(Connection c, String email) throws SQLException {
        return exists(c, "SELECT 1 FROM users WHERE email = ?", email);
    }

    public boolean existsByPhone(Connection c, String phone) throws SQLException {
        return exists(c, "SELECT 1 FROM users WHERE phone = ?", phone);
    }

    public void updateLoginState(Connection c, User u) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?")) {
            ps.setInt(1, u.getFailedAttempts());
            ps.setLong(2, u.getLockedUntil());
            ps.setLong(3, u.getId());
            ps.executeUpdate();
        }
    }

    public void updatePassword(Connection c, User u) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET password_hash = ?, salt = ?, must_change_password = ? WHERE id = ?")) {
            ps.setString(1, u.getPasswordHash());
            ps.setString(2, u.getSalt());
            ps.setInt(3, u.isMustChangePassword() ? 1 : 0);
            ps.setLong(4, u.getId());
            ps.executeUpdate();
        }
    }

    /** Customers whose name, username, email, phone or any account number contains the query. */
    public List<User> searchCustomers(Connection c, String query, int limit) throws SQLException {
        String like = "%" + query.toLowerCase().replace("%", "") + "%";
        String sql = "SELECT " + COLUMNS + " FROM users u WHERE role = 'CUSTOMER' AND ("
                + "lower(full_name) LIKE ? OR username LIKE ? OR email LIKE ? OR phone LIKE ? "
                + "OR EXISTS (SELECT 1 FROM accounts a WHERE a.user_id = u.id AND a.account_number LIKE ?)) "
                + "ORDER BY created_at DESC LIMIT ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) {
                ps.setString(i, like);
            }
            ps.setInt(6, limit);
            return list(ps);
        }
    }

    public List<User> findByRole(Connection c, Role role) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM users WHERE role = ?")) {
            ps.setString(1, role.name());
            return list(ps);
        }
    }

    public int countCustomers(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE role = 'CUSTOMER'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private Optional<User> findOne(Connection c, String sql, Object param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            List<User> users = list(ps);
            return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
        }
    }

    private boolean exists(Connection c, String sql, String param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<User> list(PreparedStatement ps) throws SQLException {
        List<User> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                User u = new User();
                u.setId(rs.getLong("id"));
                u.setFullName(rs.getString("full_name"));
                u.setEmail(rs.getString("email"));
                u.setPhone(rs.getString("phone"));
                u.setUsername(rs.getString("username"));
                u.setPasswordHash(rs.getString("password_hash"));
                u.setSalt(rs.getString("salt"));
                u.setRole(Role.valueOf(rs.getString("role")));
                u.setFailedAttempts(rs.getInt("failed_attempts"));
                u.setLockedUntil(rs.getLong("locked_until"));
                u.setMustChangePassword(rs.getInt("must_change_password") == 1);
                u.setCreatedAt(rs.getLong("created_at"));
                out.add(u);
            }
        }
        return out;
    }
}
