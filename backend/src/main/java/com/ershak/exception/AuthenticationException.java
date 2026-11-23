package com.ershak.exception;

/** Wrong credentials, missing or expired session. */
public class AuthenticationException extends BankException {

    public AuthenticationException(String code, String message) {
        super(code, message, 401);
    }
}
