package com.ershak.model;

import com.ershak.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** "Ersh 3al ersh": a named savings jar funded from one of the customer's accounts. */
public class SavingsGoal {

    public enum Status { ACTIVE, ACHIEVED }

    private long id;
    private long userId;
    private long accountId;
    private String name;
    private BigDecimal targetAmount = Money.ZERO;
    private BigDecimal savedAmount = Money.ZERO;
    private String targetDate;
    private Status status = Status.ACTIVE;
    private long createdAt;

    /** 0 to 100. */
    public int getProgressPercent() {
        if (targetAmount.signum() <= 0) {
            return 0;
        }
        int pct = savedAmount.multiply(BigDecimal.valueOf(100))
                .divide(targetAmount, 0, RoundingMode.DOWN).intValue();
        return Math.min(100, Math.max(0, pct));
    }

    public boolean isReached() {
        return savedAmount.compareTo(targetAmount) >= 0;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public long getAccountId() { return accountId; }
    public void setAccountId(long accountId) { this.accountId = accountId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = Money.of(targetAmount); }
    public BigDecimal getSavedAmount() { return savedAmount; }
    public void setSavedAmount(BigDecimal savedAmount) { this.savedAmount = Money.of(savedAmount); }
    public String getTargetDate() { return targetDate; }
    public void setTargetDate(String targetDate) { this.targetDate = targetDate; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
