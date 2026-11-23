package com.ershak.dao;

import com.ershak.model.Account;
import com.ershak.model.AccountFactory;
import com.ershak.model.AccountStatus;
import com.ershak.model.AccountType;
import com.ershak.service.BankSettings;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Data access for accounts. Rows are turned into the right Account subclass through AccountFactory. */
public class AccountDao {

    private static final String COLUMNS = "id, user_id, account_number, type, balance, hold_amount, status, opened_at";

    public long insert(Connection c, Account a) throws SQLException {
        String sql = "INSERT INTO accounts (user_id, account_number, type, balance, hold_amount, status, opened_at) "
                + "VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, a.getUserId());
            ps.setString(2, a.getAccountNumber());
            ps.setString(3, a.getType().name());
            ps.setString(4, a.getBalance().toPlainString());
            ps.setString(5, a.getHoldAmount().toPlainString());
            ps.setString(6, a.getStatus().name());
            ps.setLong(7, a.getOpenedAt());
            ps.executeUpdate();
            long id = Jdbc.lastId(ps);
            a.setId(id);
            return id;
        }
    }

    /** Saves balance, hold and status after the model changed them. */
    public void update(Connection c, Account a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE accounts SET balance = ?, hold_amount = ?, status = ? WHERE id = ?")) {
            ps.setString(1, a.getBalance().toPlainString());
            ps.setString(2, a.getHoldAmount().toPlainString());
            ps.setString(3, a.getStatus().name());
            ps.setLong(4, a.getId());
            ps.executeUpdate();
        }
    }

    public Optional<Account> findById(Connection c, long id, BankSettings s) throws SQLException {
        return one(c, "SELECT " + COLUMNS + " FROM accounts WHERE id = ?", id, s);
    }

    public Optional<Account> findByNumber(Connection c, String number, BankSettings s) throws SQLException {
        return one(c, "SELECT " + COLUMNS + " FROM accounts WHERE account_number = ?", number, s);
    }

    public List<Account> findByUser(Connection c, long userId, BankSettings s) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM accounts WHERE user_id = ? AND status <> 'CLOSED' ORDER BY id")) {
            ps.setLong(1, userId);
            return list(ps, s);
        }
    }

    public List<Account> findAllActive(Connection c, BankSettings s) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM accounts WHERE status = 'ACTIVE' ORDER BY id")) {
            return list(ps, s);
        }
    }

    public List<Account> findAllOpen(Connection c, BankSettings s) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM accounts WHERE status <> 'CLOSED' ORDER BY id")) {
            return list(ps, s);
        }
    }

    public boolean numberExists(Connection c, String number) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM accounts WHERE account_number = ?")) {
            ps.setString(1, number);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Optional<Account> one(Connection c, String sql, Object param, BankSettings s) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            List<Account> l = list(ps, s);
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
    }

    private List<Account> list(PreparedStatement ps, BankSettings s) throws SQLException {
        List<Account> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Account a = AccountFactory.create(AccountType.valueOf(rs.getString("type")), s);
                a.setId(rs.getLong("id"));
                a.setUserId(rs.getLong("user_id"));
                a.setAccountNumber(rs.getString("account_number"));
                a.setBalance(new BigDecimal(rs.getString("balance")));
                a.setHoldAmount(new BigDecimal(rs.getString("hold_amount")));
                a.setStatus(AccountStatus.valueOf(rs.getString("status")));
                a.setOpenedAt(rs.getLong("opened_at"));
                out.add(a);
            }
        }
        return out;
    }
}
