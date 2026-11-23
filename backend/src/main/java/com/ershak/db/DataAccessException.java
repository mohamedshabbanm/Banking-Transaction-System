package com.ershak.db;

import java.sql.SQLException;

/** Wraps a checked {@link SQLException} so services don't have to declare it everywhere. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(SQLException cause) {
        super("Database error: " + cause.getMessage(), cause);
    }
}
