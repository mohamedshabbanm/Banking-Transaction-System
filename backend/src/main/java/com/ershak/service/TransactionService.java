package com.ershak.service;

import com.ershak.db.Database;
import com.ershak.exception.BankException;
import com.ershak.exception.InvalidAmountException;
import com.ershak.exception.LimitExceededException;
import com.ershak.exception.NotFoundException;
import com.ershak.exception.ValidationException;
import com.ershak.fraud.FraudContext;
import com.ershak.fraud.FraudEngine;
import com.ershak.fraud.RiskAssessment;
import com.ershak.fraud.RiskDecision;
import com.ershak.model.Account;
import com.ershak.model.AccountStatus;
import com.ershak.model.FraudEvent;
import com.ershak.model.Transaction;
import com.ershak.model.TransactionStatus;
import com.ershak.model.TransactionType;
import com.ershak.model.User;
import com.ershak.security.Hashing;
import com.ershak.util.BankClock;
import com.ershak.util.Money;
import com.ershak.util.PdfWriter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deposits, withdrawals and transfers, with the Fraud Guard in front of every outgoing transaction.
 * <p>
 * Flow for money going out:
 * validate → check limits → score with the Fraud Guard →
 * ALLOW: move the money now | OTP: hold the money and send a code | HOLD: hold the money for an officer.
 */
public class TransactionService {

    public record TransactionResult(Transaction transaction, RiskAssessment risk, String message) {
    }

    public record OtpResult(boolean success, Transaction transaction, int attemptsLeft, String message) {
    }

    public record HistoryFilter(String type, String status, String fromDate, String toDate,
                                BigDecimal minAmount, BigDecimal maxAmount, int page, int pageSize) {
    }

    public record HistoryPage(List<TransactionView> items, int page, int totalPages, int total) {
    }

    public record TransactionDetail(TransactionView view, Optional<FraudEvent> fraud, String fromAccount,
                                    String toAccount) {
    }

    private static final List<TransactionStatus> ACTIVE_STATUSES =
            List.of(TransactionStatus.COMPLETED, TransactionStatus.PENDING_OTP, TransactionStatus.PENDING_REVIEW);
    private static final List<TransactionStatus> ATTEMPT_STATUSES =
            List.of(TransactionStatus.COMPLETED, TransactionStatus.PENDING_OTP, TransactionStatus.PENDING_REVIEW,
                    TransactionStatus.REJECTED);
    private static final int OTP_ATTEMPTS = 3;
    private static final long OTP_VALIDITY_MILLIS = 2 * 60_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Database db;
    private final Repositories repos;
    private final SettingsService settings;
    private final AuditService audit;
    private final NotificationService notifications;
    private final FraudEngine fraudEngine;

    public TransactionService(Database db, Repositories repos, SettingsService settings, AuditService audit,
                              NotificationService notifications, FraudEngine fraudEngine) {
        this.db = db;
        this.repos = repos;
        this.settings = settings;
        this.audit = audit;
        this.notifications = notifications;
        this.fraudEngine = fraudEngine;
    }

    // ------------------------------------------------------------------ deposit

    public TransactionResult deposit(long userId, long accountId, BigDecimal rawAmount) {
        return deposit(userId, accountId, rawAmount, null);
    }

    public TransactionResult deposit(long userId, long accountId, BigDecimal rawAmount, String note) {
        BankSettings s = settings.current();
        String label = note == null || note.isBlank() ? "Cash deposit" : note.trim();
        BigDecimal amount = Money.requirePositive(rawAmount);
        if (amount.compareTo(s.maxDeposit()) > 0) {
            throw new InvalidAmountException("A single deposit can't be more than " + Money.format(s.maxDeposit()) + ".");
        }
        return db.inTransaction(c -> {
            Account account = AccountService.requireOwned(c, repos, s, userId, accountId);
            account.ensureActive();
            account.credit(amount);
            repos.accounts().update(c, account);

            Transaction t = newTransaction(c, TransactionType.DEPOSIT, null, account.getId(), amount, Money.ZERO,
                    userId, label);
            complete(t);
            repos.transactions().insert(c, t);
            audit.append(c, username(c, userId), "DEPOSIT", t.getReference() + " " + Money.format(amount)
                    + " to " + account.getMaskedNumber());
            return new TransactionResult(t, RiskAssessment.none(), "Deposit done.");
        });
    }

    // ------------------------------------------------------------------ withdraw

    public TransactionResult withdraw(long userId, long accountId, BigDecimal rawAmount) {
        BankSettings s = settings.current();
        BigDecimal amount = Money.requirePositive(rawAmount);
        return db.inTransaction(c -> {
            Account from = AccountService.requireOwned(c, repos, s, userId, accountId);
            from.ensureActive();
            BigDecimal used = usedToday(c, userId, TransactionType.WITHDRAW);
            if (used.add(amount).compareTo(s.dailyWithdrawLimit()) > 0) {
                throw new LimitExceededException("Daily withdrawal limit is " + Money.format(s.dailyWithdrawLimit())
                        + ". You can still withdraw " + Money.format(s.dailyWithdrawLimit().subtract(used).max(Money.ZERO))
                        + " today.");
            }
            from.checkOutgoingAllowed(repos.transactions().countOutgoingFromAccount(c, from.getId(),
                    BankClock.startOfMonth()));
            from.ensureCanCover(amount);

            FraudContext ctx = buildContext(c, s, userId, from, amount, false, false,
                    s.dailyWithdrawLimit().subtract(used));
            RiskAssessment risk = fraudEngine.assess(ctx, s);
            Transaction t = newTransaction(c, TransactionType.WITHDRAW, from.getId(), null, amount, Money.ZERO,
                    userId, "Cash withdrawal");
            return applyDecision(c, s, t, from, null, risk, userId);
        });
    }

    // ------------------------------------------------------------------ transfer

    public TransactionResult transfer(long userId, long fromAccountId, String toAccountNumber, BigDecimal rawAmount,
                                      String note) {
        BankSettings s = settings.current();
        BigDecimal amount = Money.requirePositive(rawAmount);
        String number = toAccountNumber == null ? "" : toAccountNumber.replaceAll("\\s", "");
        if (number.isEmpty()) {
            throw new ValidationException("Enter the recipient's account number.");
        }
        String cleanNote = note == null ? null : note.trim().substring(0, Math.min(note.trim().length(), 120));

        return db.inTransaction(c -> {
            Account from = AccountService.requireOwned(c, repos, s, userId, fromAccountId);
            from.ensureActive();
            Account to = repos.accounts().findByNumber(c, number, s)
                    .filter(a -> a.getStatus() != AccountStatus.CLOSED)
                    .orElseThrow(() -> new NotFoundException("Recipient account"));
            if (to.getId() == from.getId()) {
                throw new ValidationException("SAME_ACCOUNT", "You can't transfer to the same account.");
            }
            if (to.getStatus() != AccountStatus.ACTIVE) {
                throw new ValidationException("RECIPIENT_UNAVAILABLE", "The recipient account can't receive money now.");
            }
            boolean ownAccount = to.getUserId() == userId;
            BigDecimal fee = ownAccount ? Money.ZERO : s.transferFee();
            BigDecimal total = amount.add(fee);

            BigDecimal used = usedToday(c, userId, TransactionType.TRANSFER);
            if (used.add(amount).compareTo(s.dailyTransferLimit()) > 0) {
                throw new LimitExceededException("Daily transfer limit is " + Money.format(s.dailyTransferLimit())
                        + ". You can still send " + Money.format(s.dailyTransferLimit().subtract(used).max(Money.ZERO))
                        + " today.");
            }
            from.checkOutgoingAllowed(repos.transactions().countOutgoingFromAccount(c, from.getId(),
                    BankClock.startOfMonth()));
            from.ensureCanCover(total);

            RiskAssessment risk;
            if (ownAccount) {
                risk = RiskAssessment.none();
            } else {
                boolean newRecipient = !repos.transactions().hasCompletedTransferTo(c, userId, to.getId());
                FraudContext ctx = buildContext(c, s, userId, from, amount, true, newRecipient,
                        s.dailyTransferLimit().subtract(used));
                risk = fraudEngine.assess(ctx, s);
            }
            Transaction t = newTransaction(c, TransactionType.TRANSFER, from.getId(), to.getId(), amount, fee,
                    userId, cleanNote);
            return applyDecision(c, s, t, from, to, risk, userId);
        });
    }

    // ------------------------------------------------------------------ OTP

    public OtpResult confirmOtp(long userId, long transactionId, String code) {
        BankSettings s = settings.current();
        return db.inTransaction(c -> {
            Transaction t = repos.transactions().findById(c, transactionId)
                    .filter(tx -> tx.getInitiatedBy() == userId)
                    .orElseThrow(() -> new NotFoundException("Transaction"));
            if (t.getStatus() != TransactionStatus.PENDING_OTP) {
                throw new ValidationException("NOT_WAITING_FOR_OTP", "This transaction isn't waiting for a code.");
            }
            var otp = repos.otp().find(c, t.getId()).orElseThrow(() -> new NotFoundException("Code"));
            long now = BankClock.nowMillis();
            String who = username(c, userId);

            if (now > otp.expiresAt()) {
                reject(c, s, t, "OTP expired");
                audit.append(c, who, "TX_REJECTED", t.getReference() + " code expired");
                return new OtpResult(false, t, 0, "The code expired. The transfer was cancelled and nothing left your account.");
            }
            String expected = Hashing.sha256(t.getId() + ":" + (code == null ? "" : code.trim()));
            if (!expected.equals(otp.codeHash())) {
                repos.otp().incrementAttempts(c, t.getId());
                int left = OTP_ATTEMPTS - (otp.attempts() + 1);
                if (left <= 0) {
                    reject(c, s, t, "Wrong code 3 times");
                    audit.append(c, who, "TX_REJECTED", t.getReference() + " wrong OTP 3 times");
                    notifications.notify(c, userId, "LOCK", "الحرص واجب",
                            "We cancelled " + t.getReference() + " after 3 wrong codes. If it wasn't you, change your password.");
                    return new OtpResult(false, t, 0, "Wrong code 3 times. We cancelled the transfer to keep you safe.");
                }
                audit.append(c, who, "OTP_FAILED", t.getReference() + " (" + left + " left)");
                return new OtpResult(false, t, left, "Wrong code. " + left + (left == 1 ? " try" : " tries") + " left.");
            }

            repos.otp().delete(c, t.getId());
            Account from = repos.accounts().findById(c, t.getFromAccountId(), s).orElseThrow();
            Account to = t.getToAccountId() == null ? null
                    : repos.accounts().findById(c, t.getToAccountId(), s).orElseThrow();
            from.releaseHold(t.getTotalDebit());
            execute(c, t, from, to);
            audit.append(c, who, "OTP_CONFIRMED", t.getReference() + " " + Money.format(t.getAmount()));
            return new OtpResult(true, t, OTP_ATTEMPTS, "Confirmed. Money sent.");
        });
    }

    /** "Not me": the customer cancels their own pending transaction, optionally freezing the account. */
    public Transaction cancelPending(long userId, long transactionId, boolean freezeAccount) {
        BankSettings s = settings.current();
        return db.inTransaction(c -> {
            Transaction t = repos.transactions().findById(c, transactionId)
                    .filter(tx -> tx.getInitiatedBy() == userId)
                    .orElseThrow(() -> new NotFoundException("Transaction"));
            if (!t.getStatus().isPending()) {
                throw new ValidationException("NOT_PENDING", "Only a transaction on hold can be cancelled.");
            }
            reject(c, s, t, "Cancelled by customer");
            String who = username(c, userId);
            audit.append(c, who, "TX_CANCELLED", t.getReference() + " cancelled by customer");
            if (freezeAccount) {
                Account from = repos.accounts().findById(c, t.getFromAccountId(), s).orElseThrow();
                from.setStatus(AccountStatus.FROZEN);
                repos.accounts().update(c, from);
                audit.append(c, who, "ACCOUNT_FROZEN", from.getMaskedNumber() + " frozen by customer (not me)");
                notifications.notify(c, userId, "LOCK", "حسابك في أمان",
                        "We froze " + from.getMaskedNumber() + ". Call the bank to unfreeze it.");
                notifications.notifyAdmins(c, "HOLD", "Customer reported fraud",
                        who + " said " + t.getReference() + " wasn't them. Account frozen.");
            }
            return t;
        });
    }

    // ------------------------------------------------------------------ used by admins

    /** Officer approves a held transaction: the hold is released and the money moves. */
    public void approveHeld(Connection c, Transaction t) throws SQLException {
        BankSettings s = settings.current();
        Account from = repos.accounts().findById(c, t.getFromAccountId(), s).orElseThrow();
        Account to = t.getToAccountId() == null ? null
                : repos.accounts().findById(c, t.getToAccountId(), s).orElseThrow();
        from.releaseHold(t.getTotalDebit());
        try {
            if (to != null) {
                to.ensureActive();
            }
            execute(c, t, from, to);
        } catch (BankException e) {
            repos.accounts().update(c, from);
            t.setStatus(TransactionStatus.FAILED);
            repos.transactions().updateStatus(c, t);
        }
    }

    /** Officer rejects (or the system cancels) a pending transaction: the held money is released. */
    public void reject(Connection c, BankSettings s, Transaction t, String reason) throws SQLException {
        Account from = repos.accounts().findById(c, t.getFromAccountId(), s).orElseThrow();
        from.releaseHold(t.getTotalDebit());
        repos.accounts().update(c, from);
        t.setStatus(TransactionStatus.REJECTED);
        t.setCompletedAt(BankClock.nowMillis());
        repos.transactions().updateStatus(c, t);
        repos.otp().delete(c, t.getId());
    }

    // ------------------------------------------------------------------ history

    public HistoryPage history(long userId, long accountId, HistoryFilter f) {
        BankSettings s = settings.current();
        return db.read(c -> {
            Account account = AccountService.requireOwned(c, repos, s, userId, accountId);
            long from = f.fromDate() == null || f.fromDate().isBlank() ? 0 : dayStart(f.fromDate());
            long to = f.toDate() == null || f.toDate().isBlank() ? Long.MAX_VALUE : dayStart(f.toDate()) + 86_400_000L;
            List<Transaction> all = repos.transactions().findForAccount(c, account.getId(), from, to);
            List<TransactionView> filtered = new ArrayList<>();
            Map<Long, String> names = new HashMap<>();
            for (Transaction t : all) {
                if (f.type() != null && !f.type().isBlank() && !t.getType().name().equalsIgnoreCase(f.type())) {
                    continue;
                }
                if (f.status() != null && !f.status().isBlank() && !t.getStatus().name().equalsIgnoreCase(f.status())) {
                    continue;
                }
                if (f.minAmount() != null && t.getAmount().compareTo(f.minAmount()) < 0) {
                    continue;
                }
                if (f.maxAmount() != null && t.getAmount().compareTo(f.maxAmount()) > 0) {
                    continue;
                }
                filtered.add(view(c, s, t, account.getId(), names));
            }
            int size = f.pageSize() <= 0 ? 20 : f.pageSize();
            int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) size));
            int page = Math.min(Math.max(1, f.page()), totalPages);
            int start = (page - 1) * size;
            List<TransactionView> items = filtered.subList(Math.min(start, filtered.size()),
                    Math.min(start + size, filtered.size()));
            return new HistoryPage(List.copyOf(items), page, totalPages, filtered.size());
        });
    }

    /** The latest activity across all of a user's accounts, for the home screen. */
    public List<TransactionView> recentForUser(long userId, int limit) {
        BankSettings s = settings.current();
        return db.read(c -> {
            List<Account> mine = repos.accounts().findByUser(c, userId, s);
            List<Long> ids = mine.stream().map(Account::getId).toList();
            Map<Long, String> names = new HashMap<>();
            List<TransactionView> out = new ArrayList<>();
            for (Transaction t : repos.transactions().findRecentForUser(c, userId, limit)) {
                long perspective = t.getFromAccountId() != null && ids.contains(t.getFromAccountId())
                        ? t.getFromAccountId() : t.getToAccountId();
                out.add(view(c, s, t, perspective, names));
            }
            return out;
        });
    }

    public TransactionDetail detail(long userId, long transactionId) {
        BankSettings s = settings.current();
        return db.read(c -> {
            Transaction t = repos.transactions().findById(c, transactionId).orElseThrow(() -> new NotFoundException("Transaction"));
            Account from = t.getFromAccountId() == null ? null : repos.accounts().findById(c, t.getFromAccountId(), s).orElse(null);
            Account to = t.getToAccountId() == null ? null : repos.accounts().findById(c, t.getToAccountId(), s).orElse(null);
            boolean mine = (from != null && from.getUserId() == userId) || (to != null && to.getUserId() == userId);
            if (!mine) {
                throw new NotFoundException("Transaction");
            }
            long perspective = from != null && from.getUserId() == userId ? from.getId() : to.getId();
            // Customers only see fraud reasons for their own outgoing transactions.
            Optional<FraudEvent> fraud = from != null && from.getUserId() == userId
                    ? repos.fraud().findByTransaction(c, t.getId()) : Optional.empty();
            return new TransactionDetail(view(c, s, t, perspective, new HashMap<>()), fraud,
                    from == null ? null : from.getMaskedNumber(), to == null ? null : to.getMaskedNumber());
        });
    }

    public String statementCsv(long userId, long accountId, String fromDate, String toDate) {
        HistoryPage page = history(userId, accountId,
                new HistoryFilter(null, null, fromDate, toDate, null, null, 1, Integer.MAX_VALUE));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder csv = new StringBuilder("Date,Reference,Type,Description,Direction,Amount (EGP),Fee (EGP),Status\n");
        for (TransactionView v : page.items()) {
            csv.append(BankClock.toZoned(v.createdAt()).format(fmt)).append(',')
                    .append(v.reference()).append(',')
                    .append(v.type()).append(',')
                    .append(csvCell(v.title())).append(',')
                    .append(v.direction()).append(',')
                    .append("OUT".equals(v.direction()) ? "-" : "").append(v.amount().toPlainString()).append(',')
                    .append(v.fee().toPlainString()).append(',')
                    .append(v.status()).append('\n');
        }
        return csv.toString();
    }


    /**
     * A printable statement for one account: header, a table of every transaction in the period, and totals.
     * Written by hand on {@link PdfWriter}, so it needs no PDF library.
     */
    public byte[] statementPdf(long userId, long accountId, String fromDate, String toDate) {
        HistoryPage page = history(userId, accountId,
                new HistoryFilter(null, null, fromDate, toDate, null, null, 1, Integer.MAX_VALUE));
        BankSettings s = settings.current();
        String[] who = new String[2];
        Account account = db.read(c -> {
            Account a = AccountService.requireOwned(c, repos, s, userId, accountId);
            who[0] = repos.users().findById(c, userId).map(User::getFullName).orElse("");
            return a;
        });

        final int[] ink = {223, 230, 239};
        final int[] dark = {11, 12, 15};
        final int[] panel = {27, 32, 40};
        final int[] muted = {126, 138, 153};
        final int[] accent = {122, 160, 199};
        final int[] text = {30, 36, 46};
        final int[] bad = {190, 70, 80};
        final int[] ok = {40, 130, 80};
        final double left = 40, right = PdfWriter.WIDTH - 40;
        final double cDate = left, cRef = left + 82, cDesc = left + 188, cAmtR = right - 128, cFeeR = right - 82, cStatX = right - 70;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        BigDecimal in = Money.ZERO;
        BigDecimal out = Money.ZERO;
        for (TransactionView v : page.items()) {
            if (!"COMPLETED".equals(v.status())) {
                continue;
            }
            if ("OUT".equals(v.direction())) {
                out = out.add(v.amount()).add(v.fee());
            } else {
                in = in.add(v.amount());
            }
        }

        PdfWriter pdf = new PdfWriter();
        pdf.rect(0, 0, PdfWriter.WIDTH, 96, dark);
        pdf.text(left, 46, 24, true, "Ershak", ink);
        pdf.text(left, 66, 10, false, "Stack your coins. We guard them.", accent);
        pdf.textRight(right, 44, 16, true, "Account statement", ink);
        pdf.textRight(right, 62, 9, false, "Generated " + BankClock.toZoned(BankClock.nowMillis()).format(fmt), muted);

        double y = 128;
        pdf.text(left, y, 9, false, "CUSTOMER", muted);
        pdf.text(left, y + 16, 13, true, who[0], text);
        pdf.text(left + 230, y, 9, false, "ACCOUNT", muted);
        pdf.text(left + 230, y + 16, 13, true, account.getType().name().charAt(0)
                + account.getType().name().substring(1).toLowerCase() + " " + account.getAccountNumber(), text);
        String period = (fromDate == null || fromDate.isBlank() ? "Beginning" : fromDate) + "  to  "
                + (toDate == null || toDate.isBlank() ? "today" : toDate);
        pdf.text(left, y + 44, 9, false, "PERIOD", muted);
        pdf.text(left, y + 60, 11, false, period, text);
        pdf.text(left + 230, y + 44, 9, false, "BALANCE NOW", muted);
        pdf.text(left + 230, y + 60, 11, true, Money.format(account.getBalance()), text);

        double boxY = y + 84;
        double boxW = (right - left - 20) / 3;
        String[][] boxes = {{"MONEY IN", Money.format(in)}, {"MONEY OUT (WITH FEES)", Money.format(out)},
                {"TRANSACTIONS", String.valueOf(page.total())}};
        for (int i = 0; i < 3; i++) {
            double bx = left + i * (boxW + 10);
            pdf.rect(bx, boxY, boxW, 46, new int[]{240, 243, 247});
            pdf.text(bx + 12, boxY + 16, 8, false, boxes[i][0], muted);
            pdf.text(bx + 12, boxY + 35, 13, true, boxes[i][1], i == 0 ? ok : i == 1 ? bad : text);
        }

        double rowY = boxY + 76;
        double top = rowY;
        double bottom = PdfWriter.HEIGHT - 56;
        java.util.function.DoubleConsumer header = yy -> {
            pdf.rect(left, yy - 14, right - left, 22, panel);
            pdf.text(cDate + 6, yy, 8.5, true, "DATE", ink);
            pdf.text(cRef, yy, 8.5, true, "REFERENCE", ink);
            pdf.text(cDesc, yy, 8.5, true, "DESCRIPTION", ink);
            pdf.textRight(cAmtR, yy, 8.5, true, "AMOUNT (EGP)", ink);
            pdf.textRight(cFeeR, yy, 8.5, true, "FEE", ink);
            pdf.text(cStatX, yy, 8.5, true, "STATUS", ink);
        };
        header.accept(rowY);
        rowY += 26;

        if (page.items().isEmpty()) {
            pdf.text(left + 6, rowY, 10, false, "No transactions in this period.", muted);
        }
        for (TransactionView v : page.items()) {
            if (rowY > bottom) {
                pdf.newPage();
                rowY = 60;
                header.accept(rowY);
                rowY += 26;
            }
            boolean outgoing = "OUT".equals(v.direction());
            pdf.text(cDate + 6, rowY, 8.5, false, BankClock.toZoned(v.createdAt()).format(fmt), text);
            pdf.text(cRef, rowY, 8.5, false, v.reference(), text);
            pdf.text(cDesc, rowY, 8.5, false, PdfWriter.fit(v.title(), cAmtR - 88 - cDesc, 8.5, false), text);
            pdf.textRight(cAmtR, rowY, 8.5, true, (outgoing ? "-" : "+") + String.format(java.util.Locale.ROOT, "%,.2f", v.amount()),
                    outgoing ? bad : ok);
            pdf.textRight(cFeeR, rowY, 8.5, false, v.fee().signum() > 0 ? String.format(java.util.Locale.ROOT, "%,.2f", v.fee()) : "-", muted);
            pdf.text(cStatX, rowY, 8, false, statusLabel(v.status()), "COMPLETED".equals(v.status()) ? ok : muted);
            pdf.line(left, rowY + 7, right, rowY + 7, 0.4, new int[]{225, 230, 236});
            rowY += 19;
        }

        int pages = pdf.pageCount();
        for (int i = 1; i <= pages; i++) {
            pdf.usePage(i);
            pdf.line(left, PdfWriter.HEIGHT - 40, right, PdfWriter.HEIGHT - 40, 0.5, new int[]{200, 207, 216});
            pdf.text(left, PdfWriter.HEIGHT - 26, 8, false,
                    "Ershak is an education project. No real money or bank connection.", muted);
            pdf.textRight(right, PdfWriter.HEIGHT - 26, 8, false, "Page " + i + " of " + pages, muted);
        }
        return pdf.toBytes();
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "COMPLETED" -> "Completed";
            case "PENDING_OTP" -> "Needs code";
            case "PENDING_REVIEW" -> "Under review";
            case "REJECTED" -> "Rejected";
            default -> "Failed";
        };
    }

    // ------------------------------------------------------------------ internals

    private TransactionResult applyDecision(Connection c, BankSettings s, Transaction t, Account from, Account to,
                                            RiskAssessment risk, long userId) throws SQLException {
        String who = username(c, userId);
        String summary = t.getReference() + " " + Money.format(t.getAmount())
                + (to == null ? " withdrawal" : " to " + to.getMaskedNumber());
        switch (risk.decision()) {
            case ALLOW -> {
                execute(c, t, from, to);
                audit.append(c, who, t.getType().name(), summary + " (risk " + risk.score() + ")");
                saveFraud(c, t, risk);
                return new TransactionResult(t, risk, "Done. Money sent.");
            }
            case OTP -> {
                from.placeHold(t.getTotalDebit());
                repos.accounts().update(c, from);
                t.setStatus(TransactionStatus.PENDING_OTP);
                repos.transactions().insert(c, t);
                String code = String.format("%06d", RANDOM.nextInt(1_000_000));
                repos.otp().upsert(c, t.getId(), Hashing.sha256(t.getId() + ":" + code),
                        BankClock.nowMillis() + OTP_VALIDITY_MILLIS);
                notifications.sms(c, userId, "Ershak code: " + code + " to confirm " + Money.format(t.getAmount())
                        + ". Valid 2 minutes. Never share it with anyone. الحرص واجب");
                audit.append(c, who, "OTP_REQUIRED", summary + " (risk " + risk.score() + ")");
                saveFraud(c, t, risk);
                return new TransactionResult(t, risk, "Enter the code we sent to confirm.");
            }
            case HOLD -> {
                from.placeHold(t.getTotalDebit());
                repos.accounts().update(c, from);
                t.setStatus(TransactionStatus.PENDING_REVIEW);
                repos.transactions().insert(c, t);
                audit.append(c, who, "TX_HELD", summary + " (risk " + risk.score() + ")");
                saveFraud(c, t, risk);
                notifications.notify(c, userId, "HOLD", "سدّه واستريح",
                        "We paused " + t.getReference() + " (" + Money.format(t.getAmount())
                                + ") until a bank officer reviews it. Your money is held, not sent.");
                notifications.notifyAdmins(c, "HOLD", "Transaction needs review",
                        who + ": " + summary + ", risk " + risk.score());
                return new TransactionResult(t, risk, "We paused this transfer for review.");
            }
            default -> throw new IllegalStateException();
        }
    }

    /** Moves the money and marks the transaction completed. Caller has already released any hold. */
    private void execute(Connection c, Transaction t, Account from, Account to) throws SQLException {
        from.debit(t.getTotalDebit());
        repos.accounts().update(c, from);
        if (to != null) {
            to.credit(t.getAmount());
            repos.accounts().update(c, to);
        }
        complete(t);
        if (t.getId() == 0) {
            repos.transactions().insert(c, t);
        } else {
            repos.transactions().updateStatus(c, t);
        }
        if (to != null && to.getUserId() != t.getInitiatedBy()) {
            notifications.notify(c, to.getUserId(), "RECEIVED", "Money received",
                    Money.format(t.getAmount()) + " arrived in " + to.getMaskedNumber() + ".");
        }
    }

    private void saveFraud(Connection c, Transaction t, RiskAssessment risk) throws SQLException {
        if (t.getType().isOutgoing() && (risk.score() > 0 || risk.decision() != RiskDecision.ALLOW)) {
            repos.fraud().insert(c, t.getId(), risk.score(), risk.triggered(), risk.decision().name(),
                    BankClock.nowMillis());
        }
    }

    private FraudContext buildContext(Connection c, BankSettings s, long userId, Account from, BigDecimal amount,
                                      boolean transfer, boolean newRecipient, BigDecimal remainingLimit)
            throws SQLException {
        long now = BankClock.nowMillis();
        List<Transaction> past = repos.transactions().findOutgoingByUser(c, userId, now - 30L * 86_400_000L, null,
                List.of(TransactionStatus.COMPLETED));
        BigDecimal average = Money.ZERO;
        if (!past.isEmpty()) {
            BigDecimal sum = past.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            average = sum.divide(BigDecimal.valueOf(past.size()), 2, RoundingMode.HALF_EVEN);
        }
        int recent = repos.transactions().findOutgoingByUser(c, userId, now - s.r2WindowMinutes() * 60_000L, null,
                ATTEMPT_STATUSES).size();
        return new FraudContext(amount, from.getBalance(), BankClock.now(), average, past.size(), recent,
                newRecipient, transfer, remainingLimit.max(Money.ZERO));
    }

    private BigDecimal usedToday(Connection c, long userId, TransactionType type) throws SQLException {
        return repos.transactions().findOutgoingByUser(c, userId, BankClock.startOfToday(), type, ACTIVE_STATUSES)
                .stream().map(Transaction::getAmount).reduce(Money.ZERO, BigDecimal::add);
    }

    private Transaction newTransaction(Connection c, TransactionType type, Long from, Long to, BigDecimal amount,
                                       BigDecimal fee, long userId, String note) throws SQLException {
        Transaction t = new Transaction(type, from, to, amount, fee, userId);
        t.setReference(ReferenceGenerator.next(c, repos));
        t.setCreatedAt(BankClock.nowMillis());
        t.setNote(note);
        return t;
    }

    private static void complete(Transaction t) {
        t.setStatus(TransactionStatus.COMPLETED);
        t.setCompletedAt(BankClock.nowMillis());
    }

    private String username(Connection c, long userId) throws SQLException {
        return repos.users().findById(c, userId).map(User::getUsername).orElse("user:" + userId);
    }

    TransactionView view(Connection c, BankSettings s, Transaction t, long perspectiveAccountId,
                         Map<Long, String> names) throws SQLException {
        boolean out = t.getFromAccountId() != null && t.getFromAccountId() == perspectiveAccountId;
        String direction = out ? "OUT" : "IN";
        String title;
        String counterparty = null;
        switch (t.getType()) {
            case DEPOSIT -> title = t.getNote() != null ? t.getNote() : "Cash deposit";
            case WITHDRAW -> title = "Cash withdrawal";
            case INTEREST -> title = "Monthly interest";
            case GOAL_DEPOSIT -> title = "Saved to goal: " + t.getNote();
            case GOAL_WITHDRAW -> title = "Moved from goal: " + t.getNote();
            default -> {
                Long other = out ? t.getToAccountId() : t.getFromAccountId();
                counterparty = other == null ? null : ownerName(c, s, other, names);
                title = counterparty == null ? "Transfer" : counterparty;
            }
        }
        return new TransactionView(t.getId(), t.getReference(), t.getType().name(), t.getStatus().name(), direction,
                t.getAmount(), out ? t.getFee() : Money.ZERO, title, counterparty, t.getNote(), t.getCreatedAt(),
                t.getCompletedAt());
    }

    private String ownerName(Connection c, BankSettings s, long accountId, Map<Long, String> cache) throws SQLException {
        if (cache.containsKey(accountId)) {
            return cache.get(accountId);
        }
        Optional<Account> a = repos.accounts().findById(c, accountId, s);
        String name = null;
        if (a.isPresent()) {
            name = repos.users().findById(c, a.get().getUserId()).map(User::getFullName).orElse(null);
            if (name != null) {
                name = name + " · " + a.get().getMaskedNumber();
            }
        }
        cache.put(accountId, name);
        return name;
    }

    private static long dayStart(String isoDate) {
        try {
            return LocalDate.parse(isoDate).atStartOfDay(BankClock.ZONE).toInstant().toEpochMilli();
        } catch (Exception e) {
            throw new ValidationException("Dates must look like 2026-11-23.");
        }
    }

    private static String csvCell(String v) {
        if (v == null) {
            return "";
        }
        String safe = v.replace("\"", "\"\"");
        // Stop spreadsheet formula injection.
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe + "\"";
    }
}
