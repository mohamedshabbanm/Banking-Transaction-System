package com.ershak.model;

import com.ershak.fraud.RuleResult;

import java.util.List;

/** The Fraud Guard's verdict on one transaction, kept for review and reporting. */
public record FraudEvent(long id, long transactionId, int score, List<RuleResult> triggeredRules,
                         String decision, Long reviewedBy, Long reviewedAt, long createdAt) {
}
