package com.ershak.service;

import com.ershak.db.Database;
import com.ershak.exception.InvalidAmountException;
import com.ershak.exception.NotFoundException;
import com.ershak.exception.ValidationException;
import com.ershak.model.Account;
import com.ershak.model.AccountFactory;
import com.ershak.model.AccountStatus;
import com.ershak.model.AccountType;
import com.ershak.model.Transaction;
import com.ershak.model.TransactionStatus;
import com.ershak.model.TransactionType;
import com.ershak.model.User;
import com.ershak.util.BankClock;
import com.ershak.util.Money;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Opening, listing and closing accounts. */
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Database db;
    private final Repositories repos;
    private final SettingsService settings;
    private final AuditService audit;
    private final NotificationService notifications;

    public AccountService(Database db, Repositories repos, SettingsService settings, AuditService audit,
                          NotificationService notifications) {
        this.db = db;
        this.repos = repos;
        this.settings = settings;
        this.audit = audit;
        this.notifications = notifications;
    }

    public Account open(long userId, AccountType type, BigDecimal initialDeposit) {
        if (type == null) {
            throw new ValidationException("Choose Savings or Current.");
        }
        BankSettings s = settings.current();
        BigDecimal deposit = Money.requirePositive(initialDeposit);
        if (deposit.compareTo(s.minOpeningDeposit()) < 0) {
            throw new InvalidAmountException("The first deposit must be at least "
                    + Money.format(s.minOpeningDeposit()) + ".");
        }
        if (deposit.compareTo(s.maxDeposit()) > 0) {
            throw new InvalidAmountException("A single deposit can't be more than " + Money.format(s.maxDeposit()) + ".");
        }
        return db.inTransaction(c -> {
            User user = repos.users().findById(c, userId).orElseThrow(() -> new NotFoundException("User"));
            long now = BankClock.nowMillis();
            Account account = AccountFactory.create(type, s);
            account.setUserId(userId);
            account.setAccountNumber(newAccountNumber(c));
            account.setStatus(AccountStatus.ACTIVE);
            account.setOpenedAt(now);
            account.credit(deposit);
            repos.accounts().insert(c, account);

            Transaction t = new Transaction(TransactionType.DEPOSIT, null, account.getId(), deposit, Money.ZERO, userId);
            t.setReference(ReferenceGenerator.next(c, repos));
            t.setStatus(TransactionStatus.COMPLETED);
            t.setNote("Opening deposit");
            t.setCreatedAt(now);
            t.setCompletedAt(now);
            repos.transactions().insert(c, t);

            audit.append(c, user.getUsername(), "ACCOUNT_OPENED",
                    type + " " + account.getMaskedNumber() + " with " + Money.format(deposit));
            notifications.notify(c, userId, "INFO", "Account opened",
                    "Your " + type.name().toLowerCase() + " account " + account.getMaskedNumber() + " is ready.");
            return account;
        });
    }

    public List<Account> listForUser(long userId) {
        BankSettings s = settings.current();
        return db.read(c -> repos.accounts().findByUser(c, userId, s));
    }

    public Account getOwned(long userId, long accountId) {
        BankSettings s = settings.current();
        return db.read(c -> requireOwned(c, repos, s, userId, accountId));
    }

    public void close(long userId, long accountId) {
        BankSettings s = settings.current();
        db.inTransaction(c -> {
            Account a = requireOwned(c, repos, s, userId, accountId);
            if (a.getBalance().signum() != 0 || a.getHoldAmount().signum() != 0) {
                throw new ValidationException("ACCOUNT_NOT_EMPTY",
                        "Move all the money out first. An account can only be closed at EGP 0.00.");
            }
            a.setStatus(AccountStatus.CLOSED);
            repos.accounts().update(c, a);
            audit.append(c, audit.actor(c, userId), "ACCOUNT_CLOSED", a.getMaskedNumber());
            return null;
        });
    }

    /** Loads an account and makes sure it belongs to the user. Someone else's account looks "not found". */
    static Account requireOwned(Connection c, Repositories repos, BankSettings s, long userId, long accountId)
            throws SQLException {
        Account a = repos.accounts().findById(c, accountId, s).orElseThrow(() -> new NotFoundException("Account"));
        if (a.getUserId() != userId || a.getStatus() == AccountStatus.CLOSED) {
            throw new NotFoundException("Account");
        }
        return a;
    }

    /** 12 digits starting with 1001, unique. */
    private String newAccountNumber(Connection c) throws SQLException {
        while (true) {
            String number = "1001" + String.format("%08d", RANDOM.nextInt(100_000_000));
            if (!repos.accounts().numberExists(c, number)) {
                return number;
            }
        }
    }
}
