package com.ershak.exception;

/** Thrown when an operation touches a frozen or closed account. */
public class AccountFrozenException extends BankException {

    public AccountFrozenException(String accountNumber, String status) {
        super("ACCOUNT_" + status, "Account " + mask(accountNumber) + " is " + status.toLowerCase()
                + ". Contact the bank to continue.");
    }

    private static String mask(String number) {
        return number.length() <= 4 ? number : "•••• " + number.substring(number.length() - 4);
    }
}
