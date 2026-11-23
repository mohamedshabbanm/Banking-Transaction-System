package com.ershak.api;

import com.ershak.fraud.RiskAssessment;
import com.ershak.fraud.RuleResult;
import com.ershak.model.Account;
import com.ershak.model.SavingsGoal;
import com.ershak.model.Transaction;
import com.ershak.model.User;

import java.math.BigDecimal;
import java.util.List;

/** JSON shapes sent to the web app. Kept separate from the models so internals (like password hashes) never leak. */
public final class Dto {

    private Dto() {
    }

    public record UserDto(long id, String fullName, String username, String email, String phone, String role,
                          boolean mustChangePassword) {
        static UserDto of(User u) {
            return new UserDto(u.getId(), u.getFullName(), u.getUsername(), u.getEmail(), u.getPhone(),
                    u.getRole().name(), u.isMustChangePassword());
        }
    }

    public record AccountDto(long id, String accountNumber, String maskedNumber, String type, BigDecimal balance,
                             BigDecimal holdAmount, BigDecimal availableBalance, BigDecimal overdraftLimit,
                             String status, long openedAt) {
        static AccountDto of(Account a) {
            return new AccountDto(a.getId(), a.getAccountNumber(), a.getMaskedNumber(), a.getType().name(),
                    a.getBalance(), a.getHoldAmount(), a.getAvailableBalance(), a.getOverdraftLimit(),
                    a.getStatus().name(), a.getOpenedAt());
        }
    }

    public record TransactionDto(long id, String reference, String type, String status, BigDecimal amount,
                                 BigDecimal fee, String note, long createdAt, Long completedAt) {
        static TransactionDto of(Transaction t) {
            return new TransactionDto(t.getId(), t.getReference(), t.getType().name(), t.getStatus().name(),
                    t.getAmount(), t.getFee(), t.getNote(), t.getCreatedAt(), t.getCompletedAt());
        }
    }

    public record RiskDto(int score, String level, String decision, List<RuleResult> reasons) {
        static RiskDto of(RiskAssessment r) {
            return new RiskDto(r.score(), r.level(), r.decision().name(), r.triggered());
        }
    }

    public record TransactionResultDto(TransactionDto transaction, RiskDto risk, String message) {
    }

    public record GoalDto(long id, long accountId, String name, BigDecimal targetAmount, BigDecimal savedAmount,
                          String targetDate, String status, int progress) {
        static GoalDto of(SavingsGoal g) {
            return new GoalDto(g.getId(), g.getAccountId(), g.getName(), g.getTargetAmount(), g.getSavedAmount(),
                    g.getTargetDate(), g.getStatus().name(), g.getProgressPercent());
        }
    }
}
