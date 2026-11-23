package com.ershak.exception;

/** Thrown when an account, transaction, goal or user does not exist (or is not yours). */
public class NotFoundException extends BankException {

    public NotFoundException(String what) {
        super("NOT_FOUND", what + " not found.", 404);
    }
}
