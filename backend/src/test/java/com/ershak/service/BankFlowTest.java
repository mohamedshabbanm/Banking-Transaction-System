package com.ershak.service;

import com.ershak.db.Database;
import com.ershak.exception.AccountLockedException;
import com.ershak.exception.AuthenticationException;
import com.ershak.exception.InsufficientFundsException;
import com.ershak.exception.LimitExceededException;
import com.ershak.exception.NotFoundException;
import com.ershak.fraud.RiskDecision;
import com.ershak.model.Account;
import com.ershak.model.AccountType;
import com.ershak.model.TransactionStatus;
import com.ershak.model.User;
import com.ershak.util.BankClock;
import com.ershak.util.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests of the core services against a real (temporary) SQLite database. */
class BankFlowTest {

    private File dbFile;
    private Bank bank;
    private User ali;
    private User sara;
    private Account aliSavings;
    private Account saraCurrent;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = File.createTempFile("ershak-test", ".db");
        bank = new Bank(Database.init("jdbc:sqlite:" + dbFile.getAbsolutePath()));
        noon();
        ali = bank.auth.register("Ali Hassan", "ali@test.com", "01011111111", "ali", "Password1");
        sara = bank.auth.register("Sara Mahmoud", "sara@test.com", "01022222222", "sara", "Password1");
        aliSavings = bank.accounts.open(ali.getId(), AccountType.SAVINGS, new BigDecimal("30000"));
        saraCurrent = bank.accounts.open(sara.getId(), AccountType.CURRENT, new BigDecimal("1000"));
    }

    @AfterEach
    void tearDown() {
        BankClock.reset();
        dbFile.delete();
        new File(dbFile.getAbsolutePath() + "-wal").delete();
        new File(dbFile.getAbsolutePath() + "-shm").delete();
    }

    private void noon() {
        at(ZonedDateTime.of(2026, 11, 23, 12, 0, 0, 0, BankClock.ZONE));
    }

    private void at(ZonedDateTime t) {
        BankClock.useClock(Clock.fixed(t.toInstant(), BankClock.ZONE));
    }

    private BigDecimal balance(User u, Account a) {
        return bank.accounts.getOwned(u.getId(), a.getId()).getBalance();
    }

    @Test
    @DisplayName("Normal transfer moves money and charges the 2 EGP fee")
    void normalTransfer() {
        var r = bank.transactions.transfer(ali.getId(), aliSavings.getId(), saraCurrent.getAccountNumber(),
                new BigDecimal("1000"), "Rent");
        assertEquals(TransactionStatus.COMPLETED, r.transaction().getStatus());
        assertEquals(Money.of("28998"), balance(ali, aliSavings));
        assertEquals(Money.of("2000"), balance(sara, saraCurrent));
    }

    @Test
    @DisplayName("Big first transfer at night is held, money reserved, then released on reject")
    void suspiciousTransferIsHeldAndReleased() {
        // Ali's usual pattern: small transfers to Mona from his current account.
        User mona = bank.auth.register("Mona Khaled", "mona@test.com", "01044444444", "mona", "Password1");
        Account monaCurrent = bank.accounts.open(mona.getId(), AccountType.CURRENT, new BigDecimal("100"));
        Account aliCurrent = bank.accounts.open(ali.getId(), AccountType.CURRENT, new BigDecimal("5000"));
        for (int day = 20; day <= 22; day++) {
            at(ZonedDateTime.of(2026, 11, day, 13, 0, 0, 0, BankClock.ZONE));
            bank.transactions.transfer(ali.getId(), aliCurrent.getId(), monaCurrent.getAccountNumber(),
                    new BigDecimal("500"), null);
        }
        // Then, at 2 AM: 19x the usual amount, to an account he never paid before.
        at(ZonedDateTime.of(2026, 11, 23, 2, 14, 0, 0, BankClock.ZONE));
        var r = bank.transactions.transfer(ali.getId(), aliSavings.getId(), saraCurrent.getAccountNumber(),
                new BigDecimal("9500"), null);
        assertEquals(RiskDecision.HOLD, r.risk().decision());
        assertEquals(TransactionStatus.PENDING_REVIEW, r.transaction().getStatus());

        Account held = bank.accounts.getOwned(ali.getId(), aliSavings.getId());
        assertEquals(Money.of("30000"), held.getBalance());
        assertEquals(Money.of("9502"), held.getHoldAmount());
        assertEquals(Money.of("1000"), balance(sara, saraCurrent));

        User admin = bank.auth.register("Officer", "o@test.com", "01033333333", "officer", "Password1");
        bank.admin.reject(admin.getId(), "officer", r.transaction().getId());
        Account after = bank.accounts.getOwned(ali.getId(), aliSavings.getId());
        assertEquals(Money.ZERO, after.getHoldAmount());
        assertEquals(Money.of("30000"), after.getAvailableBalance());
    }

    @Test
    @DisplayName("Admin tightens the hold threshold, then approving a held transfer sends the money")
    void approveHeld() {
        bank.settings.update("officer", java.util.Map.of("hold_threshold", "30"));
        var r = bank.transactions.transfer(ali.getId(), aliSavings.getId(), saraCurrent.getAccountNumber(),
                new BigDecimal("9000"), null);
        assertEquals(TransactionStatus.PENDING_REVIEW, r.transaction().getStatus());
        bank.admin.approve(1, "officer", r.transaction().getId());
        assertEquals(Money.of("10000"), balance(sara, saraCurrent));
        assertEquals(Money.of("20998"), balance(ali, aliSavings));
        assertEquals(Money.ZERO, bank.accounts.getOwned(ali.getId(), aliSavings.getId()).getHoldAmount());
    }

    @Test
    @DisplayName("Medium risk needs the OTP; a wrong code 3 times cancels and releases the money")
    void otpFlow() {
        var r = bank.transactions.transfer(ali.getId(), aliSavings.getId(), saraCurrent.getAccountNumber(),
                new BigDecimal("6000"), null);
        assertEquals(RiskDecision.OTP, r.risk().decision());
        long id = r.transaction().getId();
        assertFalse(bank.transactions.confirmOtp(ali.getId(), id, "000000").success());
        assertFalse(bank.transactions.confirmOtp(ali.getId(), id, "111111").success());
        var last = bank.transactions.confirmOtp(ali.getId(), id, "222222");
        assertFalse(last.success());
        assertEquals(TransactionStatus.REJECTED, last.transaction().getStatus());
        assertEquals(Money.of("30000"), bank.accounts.getOwned(ali.getId(), aliSavings.getId()).getAvailableBalance());
    }

    @Test
    @DisplayName("Correct OTP from the simulated SMS completes the transfer")
    void otpSuccess() {
        var r = bank.transactions.transfer(ali.getId(), aliSavings.getId(), saraCurrent.getAccountNumber(),
                new BigDecimal("6000"), null);
        String sms = bank.notifications.smsInbox(ali.getId()).get(0).message();
        String code = sms.substring(sms.indexOf("code: ") + 6, sms.indexOf("code: ") + 12);
        var ok = bank.transactions.confirmOtp(ali.getId(), r.transaction().getId(), code);
        assertTrue(ok.success());
        assertEquals(Money.of("7000"), balance(sara, saraCurrent));
    }

    @Test
    @DisplayName("Not enough money and daily limit are rejected without changing anything")
    void validation() {
        assertThrows(InsufficientFundsException.class, () -> bank.transactions.withdraw(sara.getId(),
                saraCurrent.getId(), new BigDecimal("3000.01")));
        assertThrows(LimitExceededException.class, () -> bank.transactions.withdraw(ali.getId(),
                aliSavings.getId(), new BigDecimal("10000.01")));
        assertEquals(Money.of("1000"), balance(sara, saraCurrent));
    }

    @Test
    @DisplayName("Customers can't touch someone else's account")
    void ownership() {
        assertThrows(NotFoundException.class, () -> bank.transactions.withdraw(sara.getId(), aliSavings.getId(),
                new BigDecimal("10")));
    }

    @Test
    @DisplayName("El talta tabta: 3 wrong passwords lock the login")
    void lockout() {
        assertThrows(AuthenticationException.class, () -> bank.auth.login("ali", "wrong1"));
        assertThrows(AuthenticationException.class, () -> bank.auth.login("ali", "wrong2"));
        assertThrows(AccountLockedException.class, () -> bank.auth.login("ali", "wrong3"));
        assertThrows(AccountLockedException.class, () -> bank.auth.login("ali", "Password1"));
        at(ZonedDateTime.of(2026, 11, 23, 12, 16, 0, 0, BankClock.ZONE));
        assertEquals("ali", bank.auth.login("ali", "Password1").user().getUsername());
    }

    @Test
    @DisplayName("Editing the audit log directly in the database is detected")
    void auditTamperDetection() {
        assertTrue(bank.audit.verify().intact());
        bank.db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE audit_log SET details = 'nothing happened' WHERE id = 2")) {
                ps.executeUpdate();
            }
            return null;
        });
        AuditService.VerifyResult result = bank.audit.verify();
        assertFalse(result.intact());
        assertEquals(Long.valueOf(2L), result.firstBrokenId());
    }

    @Test
    @DisplayName("Reaching a savings goal marks it achieved")
    void goals() {
        var goal = bank.goals.create(ali.getId(), aliSavings.getId(), "Laptop", new BigDecimal("5000"), null);
        bank.goals.contribute(ali.getId(), goal.getId(), new BigDecimal("5000"));
        assertEquals("ACHIEVED", bank.goals.list(ali.getId()).get(0).getStatus().name());
        assertEquals(Money.of("25000"), balance(ali, aliSavings));
        bank.goals.withdraw(ali.getId(), goal.getId(), null);
        assertEquals(Money.of("30000"), balance(ali, aliSavings));
    }

    @Test
    @DisplayName("The PDF statement is a valid PDF and only the owner can get one")
    void pdfStatement() {
        bank.transactions.transfer(ali.getId(), aliSavings.getId(), saraCurrent.getAccountNumber(),
                new BigDecimal("200"), "Lunch");
        byte[] pdf = bank.transactions.statementPdf(ali.getId(), aliSavings.getId(), null, null);
        String text = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("%PDF-1.4"));
        assertTrue(text.trim().endsWith("%%EOF"));
        assertTrue(text.contains("Ali Hassan"));
        assertThrows(NotFoundException.class,
                () -> bank.transactions.statementPdf(sara.getId(), aliSavings.getId(), null, null));
    }

    @Test
    @DisplayName("A passive check does not count as activity, so polling cannot keep an idle session alive")
    void passiveCheckDoesNotRefreshSession() {
        noon();
        var session = bank.sessions.create(ali);
        long before = session.lastSeen();
        at(ZonedDateTime.of(2026, 11, 23, 12, 2, 0, 0, BankClock.ZONE));
        bank.sessions.validate(session.token(), false);
        assertEquals(before, session.lastSeen());
        bank.sessions.validate(session.token());
        assertTrue(session.lastSeen() > before);
    }
}
