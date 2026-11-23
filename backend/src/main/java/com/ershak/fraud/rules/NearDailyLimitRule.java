package com.ershak.fraud.rules;

import com.ershak.fraud.FraudContext;
import com.ershak.fraud.FraudRule;
import com.ershak.fraud.RuleResult;
import com.ershak.service.BankSettings;

import java.util.Optional;

/** R6: the transaction uses up most of what is left of today's limit. */
public class NearDailyLimitRule implements FraudRule {

    @Override
    public String id() {
        return "R6";
    }

    @Override
    public String description() {
        return "Uses most of today's remaining limit";
    }

    @Override
    public Optional<RuleResult> evaluate(FraudContext ctx, BankSettings s) {
        if (ctx.remainingDailyLimit().signum() <= 0) {
            return Optional.empty();
        }
        if (ctx.amount().compareTo(ctx.remainingDailyLimit().multiply(s.r6Ratio())) < 0) {
            return Optional.empty();
        }
        return Optional.of(new RuleResult(id(), s.r6Points(), "Close to your daily limit"));
    }
}
