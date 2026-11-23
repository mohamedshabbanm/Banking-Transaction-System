package com.ershak.exception;

/** The user is logged in but not allowed to do this (for example a customer calling an admin action). */
public class ForbiddenException extends BankException {

    public ForbiddenException() {
        super("FORBIDDEN", "You don't have permission to do this.", 403);
    }
}
