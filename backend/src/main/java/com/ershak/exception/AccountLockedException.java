package com.ershak.exception;

/** "El talta tabta": the user typed a wrong password too many times. */
public class AccountLockedException extends BankException {

    private final long lockedUntil;

    public AccountLockedException(long lockedUntil) {
        super("USER_LOCKED", "Too many wrong attempts. Your account is locked for your safety.", 423);
        this.lockedUntil = lockedUntil;
    }

    public long getLockedUntil() {
        return lockedUntil;
    }
}
