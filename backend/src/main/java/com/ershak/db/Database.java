package com.ershak.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Singleton that owns the JDBC connection settings.
 * <p>
 * Every change to money happens inside {@link #inTransaction}: either all the statements
 * succeed and are committed together, or an exception rolls them all back.
 * That is what makes a transfer atomic (ACID).
 */
public final class Database {

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private static Database instance;
    private final String url;
    private final Object writeLock = new Object();

    private Database(String url) {
        this.url = url;
    }

    /** Creates the singleton and the tables. Safe to call again (for example from tests). */
    public static synchronized Database init(String url) {
        instance = new Database(url);
        instance.createSchema();
        return instance;
    }

    public static Database get() {
        if (instance == null) {
            throw new IllegalStateException("Database.init(...) was not called");
        }
        return instance;
    }

    public Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA busy_timeout = 5000");
        }
        return c;
    }

    /** Runs work in one database transaction. Writes are serialized so balances never race. */
    public <T> T inTransaction(SqlWork<T> work) {
        synchronized (writeLock) {
            try (Connection c = connect()) {
                c.setAutoCommit(false);
                try {
                    T result = work.run(c);
                    c.commit();
                    return result;
                } catch (RuntimeException | SQLException e) {
                    c.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new DataAccessException(e);
            }
        }
    }

    /** Read-only work, no transaction needed. */
    public <T> T read(SqlWork<T> work) {
        try (Connection c = connect()) {
            return work.run(c);
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    private void createSchema() {
        String sql;
        try (InputStream in = Database.class.getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new IllegalStateException("schema.sql not found on the classpath");
            }
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read schema.sql", e);
        }
        inTransaction(c -> {
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode = WAL");
            } catch (SQLException ignored) {
                // In-memory databases don't support WAL; that's fine.
            }
            for (String statement : stripComments(sql).split(";")) {
                if (!statement.isBlank()) {
                    try (Statement s = c.createStatement()) {
                        s.execute(statement);
                    }
                }
            }
            return null;
        });
    }

    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\n")) {
            if (!line.trim().startsWith("--")) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }
}
