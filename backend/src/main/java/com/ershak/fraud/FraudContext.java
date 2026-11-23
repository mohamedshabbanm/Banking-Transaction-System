package com.ershak.fraud;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Everything the fraud rules need to know about a transaction, collected once by the service.
 * The rules only read this object; they never touch the database themselves,
 * which keeps them small and easy to unit-test.
 *
 * @param amount               amount being sent
 * @param accountBalance       sender account balance before the transaction
 * @param time                 when the transaction happens (Cairo time)
 * @param averageOutgoing30d   average outgoing amount over the last 30 days
 * @param pastOutgoingCount    completed outgoing transactions in the last 30 days
 * @param recentOutgoingCount  outgoing transactions in the recent window (rule R2), excluding this one
 * @param newRecipient         true when this is the first transfer to the destination account
 * @param transfer             true for transfers, false for withdrawals
 * @param remainingDailyLimit  how much of today's limit is left before this transaction
 */
public record FraudContext(
        BigDecimal amount,
        BigDecimal accountBalance,
        LocalDateTime time,
        BigDecimal averageOutgoing30d,
        int pastOutgoingCount,
        int recentOutgoingCount,
        boolean newRecipient,
        boolean transfer,
        BigDecimal remainingDailyLimit) {
}
