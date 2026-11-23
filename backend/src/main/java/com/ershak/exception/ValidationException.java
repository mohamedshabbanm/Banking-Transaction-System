package com.ershak.exception;

/** Invalid input that is not about money: missing fields, weak password, duplicate username. */
public class ValidationException extends BankException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String code, String message) {
        super(code, message);
    }
}
