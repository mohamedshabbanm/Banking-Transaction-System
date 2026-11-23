package com.ershak.exception;

import java.math.BigDecimal;

/** Thrown when an account cannot cover a withdrawal, transfer or goal contribution. */
public class InsufficientFundsException extends BankException {

    private final BigDecimal available;

    public InsufficientFundsException(BigDecimal available) {
        super("INSUFFICIENT_FUNDS", "Not enough balance. Available: EGP " + available.toPlainString());
        this.available = available;
    }

    public BigDecimal getAvailable() {
        return available;
    }
}
