package com.ershak.service;

import com.ershak.util.BankClock;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

/** Builds readable unique references: TXN-YYYYMMDD-NNNNNN (counter restarts every day). */
final class ReferenceGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private ReferenceGenerator() {
    }

    static String next(Connection c, Repositories repos) throws SQLException {
        String prefix = "TXN-" + BankClock.now().format(DAY) + "-";
        int n = repos.transactions().countReferencesWithPrefix(c, prefix) + 1;
        return prefix + String.format("%06d", n);
    }
}
