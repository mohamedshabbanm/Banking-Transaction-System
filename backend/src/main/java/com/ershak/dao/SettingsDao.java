package com.ershak.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Key-value store for admin-tunable settings and small bits of system state. */
public class SettingsDao {

    public Map<String, String> findAll(Connection c) throws SQLException {
        Map<String, String> out = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT key, value FROM settings");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString("key"), rs.getString("value"));
            }
        }
        return out;
    }

    public Optional<String> find(Connection c, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    public void put(Connection c, String key, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?,?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }
}
