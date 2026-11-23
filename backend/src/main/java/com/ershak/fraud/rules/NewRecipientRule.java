package com.ershak.fraud.rules;

import com.ershak.fraud.FraudContext;
import com.ershak.fraud.FraudRule;
import com.ershak.fraud.RuleResult;
import com.ershak.service.BankSettings;

import java.util.Optional;

/** R3: a large first-ever transfer to an account the customer never paid before. */
public class NewRecipientRule implements FraudRule {

    @Override
    public String id() {
        return "R3";
    }

    @Override
    public String description() {
        return "Large first transfer to a new account";
    }

    @Override
    public Optional<RuleResult> evaluate(FraudContext ctx, BankSettings s) {
        if (!ctx.transfer() || !ctx.newRecipient() || ctx.amount().compareTo(s.r3Amount()) < 0) {
            return Optional.empty();
        }
        return Optional.of(new RuleResult(id(), s.r3Points(), "First transfer to this account"));
    }
}
