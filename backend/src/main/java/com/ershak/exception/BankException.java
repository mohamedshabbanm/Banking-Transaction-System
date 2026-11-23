package com.ershak.exception;

/**
 * Base class for every business error in Ershak.
 * Each subclass carries a stable machine-readable code that the API returns
 * to the client together with a human-readable message.
 */
public class BankException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public BankException(String code, String message) {
        this(code, message, 400);
    }

    public BankException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
