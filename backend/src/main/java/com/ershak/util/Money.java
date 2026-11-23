package com.ershak.util;

import com.ershak.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money helpers. All amounts in Ershak are {@link BigDecimal} with exactly 2 decimals.
 * We never use double for money because binary floating point cannot store 0.1 exactly.
 */
public final class Money {

    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private Money() {
    }

    /** Rounds to 2 decimals (banker's rounding). */
    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal of(String value) {
        return of(new BigDecimal(value));
    }

    public static BigDecimal of(long value) {
        return of(BigDecimal.valueOf(value));
    }

    /**
     * Validates a user-entered amount: must be present, positive and have at most 2 decimals.
     */
    public static BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("Enter an amount.");
        }
        if (amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidAmountException("Amount can have at most 2 decimals.");
        }
        return of(amount);
    }

    public static boolean greaterThan(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) > 0;
    }

    public static boolean atLeast(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0;
    }

    public static String format(BigDecimal amount) {
        return "EGP " + String.format("%,.2f", amount);
    }
}
