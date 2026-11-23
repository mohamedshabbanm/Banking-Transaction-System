package com.ershak.exception;

/** Thrown when a daily, monthly or per-operation limit would be broken. */
public class LimitExceededException extends BankException {

    public LimitExceededException(String message) {
        super("LIMIT_EXCEEDED", message);
    }
}
