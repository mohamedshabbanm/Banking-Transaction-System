package com.ershak.fraud;

/** What happens to a transaction after the Fraud Guard scores it. */
public enum RiskDecision {
    /** Score below the OTP threshold: executes immediately. */
    ALLOW,
    /** Medium risk: the customer must confirm with a one-time code. */
    OTP,
    /** High risk: money is held until a bank officer approves or rejects. */
    HOLD
}
