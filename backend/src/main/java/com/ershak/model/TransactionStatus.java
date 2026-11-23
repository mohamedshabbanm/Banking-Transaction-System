package com.ershak.model;

public enum TransactionStatus {
    COMPLETED,
    PENDING_OTP,
    PENDING_REVIEW,
    REJECTED,
    FAILED;

    public boolean isPending() {
        return this == PENDING_OTP || this == PENDING_REVIEW;
    }
}
