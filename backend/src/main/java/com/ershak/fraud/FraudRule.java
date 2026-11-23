package com.ershak.fraud;

import com.ershak.service.BankSettings;

import java.util.Optional;

/**
 * Strategy pattern: every fraud rule is its own class implementing this interface.
 * Adding a new rule means writing one new class and registering it in {@link FraudEngine}.
 */
public interface FraudRule {

    /** Short id shown to admins, like "R3". */
    String id();

    /** What the rule checks, for the admin "Fraud rules" screen. */
    String description();

    /** Returns a result when the rule fires, or empty when the transaction looks normal for this rule. */
    Optional<RuleResult> evaluate(FraudContext context, BankSettings settings);
}
