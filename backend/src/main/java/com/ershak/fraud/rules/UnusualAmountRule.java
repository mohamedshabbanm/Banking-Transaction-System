package com.ershak.fraud.rules;

import com.ershak.fraud.FraudContext;
import com.ershak.fraud.FraudRule;
import com.ershak.fraud.RuleResult;
import com.ershak.service.BankSettings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/** R1: the amount is many times bigger than what this customer usually sends. */
public class UnusualAmountRule implements FraudRule {

    @Override
    public String id() {
        return "R1";
    }

    @Override
    public String description() {
        return "Amount is much bigger than the customer's 30-day average";
    }

    @Override
    public Optional<RuleResult> evaluate(FraudContext ctx, BankSettings s) {
        if (ctx.pastOutgoingCount() < s.r1MinHistory() || ctx.averageOutgoing30d().signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal threshold = ctx.averageOutgoing30d().multiply(s.r1Multiplier());
        if (ctx.amount().compareTo(threshold) < 0) {
            return Optional.empty();
        }
        int times = ctx.amount().divide(ctx.averageOutgoing30d(), 0, RoundingMode.DOWN).intValue();
        return Optional.of(new RuleResult(id(), s.r1Points(), times + "× bigger than your usual transfer"));
    }
}
