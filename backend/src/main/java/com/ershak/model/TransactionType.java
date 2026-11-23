package com.ershak.model;

public enum TransactionType {
    DEPOSIT,
    WITHDRAW,
    TRANSFER,
    GOAL_DEPOSIT,
    GOAL_WITHDRAW,
    INTEREST;

    /** Types that send money out of the customer's account to the outside world. */
    public boolean isOutgoing() {
        return this == WITHDRAW || this == TRANSFER;
    }
}
