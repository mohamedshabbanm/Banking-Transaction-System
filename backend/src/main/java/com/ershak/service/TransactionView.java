package com.ershak.service;

import java.math.BigDecimal;

/**
 * A transaction as one specific account sees it: money IN or OUT, and who was on the other side.
 */
public record TransactionView(
        long id,
        String reference,
        String type,
        String status,
        String direction,
        BigDecimal amount,
        BigDecimal fee,
        String title,
        String counterparty,
        String note,
        long createdAt,
        Long completedAt) {
}
