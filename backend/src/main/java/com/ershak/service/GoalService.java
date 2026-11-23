package com.ershak.service;

import com.ershak.db.Database;
import com.ershak.exception.InsufficientFundsException;
import com.ershak.exception.InvalidAmountException;
import com.ershak.exception.NotFoundException;
import com.ershak.exception.ValidationException;
import com.ershak.model.Account;
import com.ershak.model.SavingsGoal;
import com.ershak.model.Transaction;
import com.ershak.model.TransactionStatus;
import com.ershak.model.TransactionType;
import com.ershak.util.BankClock;
import com.ershak.util.Money;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** "Ersh 3al ersh": savings goals (jars) funded from the customer's own accounts. */
public class GoalService {

    private final Database db;
    private final Repositories repos;
    private final SettingsService settings;
    private final AuditService audit;
    private final NotificationService notifications;

    public GoalService(Database db, Repositories repos, SettingsService settings, AuditService audit,
                       NotificationService notifications) {
        this.db = db;
        this.repos = repos;
        this.settings = settings;
        this.audit = audit;
        this.notifications = notifications;
    }

    public List<SavingsGoal> list(long userId) {
        return db.read(c -> repos.goals().findByUser(c, userId));
    }

    public SavingsGoal create(long userId, long accountId, String name, BigDecimal target, String targetDate) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty() || cleanName.length() > 40) {
            throw new ValidationException("Give the goal a name up to 40 characters.");
        }
        BigDecimal amount = Money.requirePositive(target);
        if (targetDate != null && !targetDate.isBlank()) {
            try {
                if (LocalDate.parse(targetDate).isBefore(BankClock.now().toLocalDate())) {
                    throw new ValidationException("The target date can't be in the past.");
                }
            } catch (java.time.format.DateTimeParseException e) {
                throw new ValidationException("Dates must look like 2026-11-23.");
            }
        }
        BankSettings s = settings.current();
        return db.inTransaction(c -> {
            AccountService.requireOwned(c, repos, s, userId, accountId);
            SavingsGoal g = new SavingsGoal();
            g.setUserId(userId);
            g.setAccountId(accountId);
            g.setName(cleanName);
            g.setTargetAmount(amount);
            g.setTargetDate(targetDate == null || targetDate.isBlank() ? null : targetDate);
            g.setCreatedAt(BankClock.nowMillis());
            repos.goals().insert(c, g);
            audit.append(c, audit.actor(c, userId), "GOAL_CREATED", cleanName + " target " + Money.format(amount));
            return g;
        });
    }

    public SavingsGoal contribute(long userId, long goalId, BigDecimal rawAmount) {
        BigDecimal amount = Money.requirePositive(rawAmount);
        BankSettings s = settings.current();
        return db.inTransaction(c -> {
            SavingsGoal g = owned(c, userId, goalId);
            Account account = AccountService.requireOwned(c, repos, s, userId, g.getAccountId());
            account.ensureActive();
            // Goals are funded from real money only, never from the overdraft.
            BigDecimal own = account.getBalance().subtract(account.getHoldAmount());
            if (amount.compareTo(own) > 0) {
                throw new InsufficientFundsException(own.max(Money.ZERO));
            }
            account.debit(amount);
            repos.accounts().update(c, account);
            boolean wasActive = g.getStatus() == SavingsGoal.Status.ACTIVE;
            g.setSavedAmount(g.getSavedAmount().add(amount));
            if (g.isReached()) {
                g.setStatus(SavingsGoal.Status.ACHIEVED);
            }
            repos.goals().update(c, g);
            record(c, TransactionType.GOAL_DEPOSIT, account.getId(), null, amount, userId, g.getName());
            if (wasActive && g.getStatus() == SavingsGoal.Status.ACHIEVED) {
                notifications.notify(c, userId, "GOAL", "حطيت القرش عالقرش ووصلت",
                        "You reached your goal \"" + g.getName() + "\": " + Money.format(g.getTargetAmount()) + ".");
            }
            return g;
        });
    }

    /** Moves money from the goal back to its account. A null amount means everything. */
    public SavingsGoal withdraw(long userId, long goalId, BigDecimal rawAmount) {
        BankSettings s = settings.current();
        return db.inTransaction(c -> {
            SavingsGoal g = owned(c, userId, goalId);
            BigDecimal amount = rawAmount == null ? g.getSavedAmount() : Money.requirePositive(rawAmount);
            if (amount.signum() <= 0) {
                throw new InvalidAmountException("This goal is empty.");
            }
            if (amount.compareTo(g.getSavedAmount()) > 0) {
                throw new InsufficientFundsException(g.getSavedAmount());
            }
            Account account = AccountService.requireOwned(c, repos, s, userId, g.getAccountId());
            account.ensureActive();
            account.credit(amount);
            repos.accounts().update(c, account);
            g.setSavedAmount(g.getSavedAmount().subtract(amount));
            g.setStatus(g.isReached() ? SavingsGoal.Status.ACHIEVED : SavingsGoal.Status.ACTIVE);
            repos.goals().update(c, g);
            record(c, TransactionType.GOAL_WITHDRAW, null, account.getId(), amount, userId, g.getName());
            return g;
        });
    }

    public void delete(long userId, long goalId) {
        db.inTransaction(c -> {
            SavingsGoal g = owned(c, userId, goalId);
            if (g.getSavedAmount().signum() > 0) {
                throw new ValidationException("GOAL_NOT_EMPTY", "Move the money back to your account before deleting the goal.");
            }
            repos.goals().delete(c, g.getId());
            return null;
        });
    }

    private SavingsGoal owned(Connection c, long userId, long goalId) throws SQLException {
        return repos.goals().findById(c, goalId).filter(g -> g.getUserId() == userId)
                .orElseThrow(() -> new NotFoundException("Goal"));
    }

    private void record(Connection c, TransactionType type, Long from, Long to, BigDecimal amount, long userId,
                        String goalName) throws SQLException {
        Transaction t = new Transaction(type, from, to, amount, Money.ZERO, userId);
        t.setReference(ReferenceGenerator.next(c, repos));
        t.setStatus(TransactionStatus.COMPLETED);
        long now = BankClock.nowMillis();
        t.setCreatedAt(now);
        t.setCompletedAt(now);
        t.setNote(goalName);
        repos.transactions().insert(c, t);
        audit.append(c, audit.actor(c, userId), type.name(), t.getReference() + " " + Money.format(amount) + " " + goalName);
    }
}
