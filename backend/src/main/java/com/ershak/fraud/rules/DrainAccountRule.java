package com.ershak.fraud.rules;

import com.ershak.fraud.FraudContext;
import com.ershak.fraud.FraudRule;
import com.ershak.fraud.RuleResult;
import com.ershak.service.BankSettings;

import java.util.Optional;

/** R5: the transaction would empty (almost) the whole account. */
public class DrainAccountRule implements FraudRule {

    @Override
    public String id() {
        return "R5";
    }

    @Override
    public String description() {
        return "Takes nearly the whole account balance";
    }

    @Override
    public Optional<RuleResult> evaluate(FraudContext ctx, BankSettings s) {
        if (ctx.accountBalance().signum() <= 0) {
            return Optional.empty();
        }
        if (ctx.amount().compareTo(ctx.accountBalance().multiply(s.r5Ratio())) < 0) {
            return Optional.empty();
        }
        return Optional.of(new RuleResult(id(), s.r5Points(), "Empties almost your whole balance"));
    }
}
