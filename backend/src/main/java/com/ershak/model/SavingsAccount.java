package com.ershak.model;

import com.ershak.exception.LimitExceededException;
import com.ershak.util.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * "El Ersh El Abyad": a savings account.
 * No overdraft, a limited number of outgoing operations per month, and monthly interest.
 */
public class SavingsAccount extends Account {

    private final int monthlyOutgoingLimit;
    private final BigDecimal annualInterestRate;

    public SavingsAccount(int monthlyOutgoingLimit, BigDecimal annualInterestRate) {
        this.monthlyOutgoingLimit = monthlyOutgoingLimit;
        this.annualInterestRate = annualInterestRate;
    }

    @Override
    public AccountType getType() {
        return AccountType.SAVINGS;
    }

    @Override
    public BigDecimal getOverdraftLimit() {
        return Money.ZERO;
    }

    @Override
    public void checkOutgoingAllowed(int outgoingThisMonth) {
        if (outgoingThisMonth >= monthlyOutgoingLimit) {
            throw new LimitExceededException("Savings accounts allow " + monthlyOutgoingLimit
                    + " withdrawals or transfers per month. Use a current account for daily spending.");
        }
    }

    @Override
    public BigDecimal monthlyInterest() {
        if (getBalance().signum() <= 0) {
            return Money.ZERO;
        }
        return getBalance().multiply(annualInterestRate)
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_EVEN);
    }

    public int getMonthlyOutgoingLimit() {
        return monthlyOutgoingLimit;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }
}
