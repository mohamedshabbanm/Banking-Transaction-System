package com.ershak.dao;

import com.ershak.model.Notification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** In-app notifications and the simulated SMS inbox. */
public class NotificationDao {

    public record Sms(long id, String message, long createdAt) {
    }

    public void insert(Connection c, long userId, String kind, String title, String message, long now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO notifications "
                + "(user_id, kind, title, message, is_read, created_at) VALUES (?,?,?,?,0,?)")) {
            ps.setLong(1, userId);
            ps.setString(2, kind);
            ps.setString(3, title);
            ps.setString(4, message);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    public List<Notification> findByUser(Connection c, long userId, int limit) throws SQLException {
        List<Notification> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM notifications WHERE user_id = ? "
                + "ORDER BY created_at DESC, id DESC LIMIT ?")) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Notification(rs.getLong("id"), rs.getLong("user_id"), rs.getString("kind"),
                            rs.getString("title"), rs.getString("message"), rs.getInt("is_read") == 1,
                            rs.getLong("created_at")));
                }
            }
        }
        return out;
    }

    public int countUnread(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void markAllRead(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE notifications SET is_read = 1 WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    public void insertSms(Connection c, long userId, String message, long now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sms_messages (user_id, message, created_at) VALUES (?,?,?)")) {
            ps.setLong(1, userId);
            ps.setString(2, message);
            ps.setLong(3, now);
            ps.executeUpdate();
        }
    }

    public List<Sms> findSms(Connection c, long userId, int limit) throws SQLException {
        List<Sms> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM sms_messages WHERE user_id = ? "
                + "ORDER BY created_at DESC, id DESC LIMIT ?")) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Sms(rs.getLong("id"), rs.getString("message"), rs.getLong("created_at")));
                }
            }
        }
        return out;
    }
}
