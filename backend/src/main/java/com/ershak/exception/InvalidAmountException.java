package com.ershak.exception;

/** Thrown for zero, negative, malformed or out-of-range amounts. */
public class InvalidAmountException extends BankException {

    public InvalidAmountException(String message) {
        super("INVALID_AMOUNT", message);
    }
}
