package com.ershak.fraud.rules;

import com.ershak.fraud.FraudContext;
import com.ershak.fraud.FraudRule;
import com.ershak.fraud.RuleResult;
import com.ershak.service.BankSettings;

import java.util.Optional;

/** R2: several outgoing transactions in a very short time, typical of a stolen session. */
public class RapidFireRule implements FraudRule {

    @Override
    public String id() {
        return "R2";
    }

    @Override
    public String description() {
        return "Many outgoing transactions in a few minutes";
    }

    @Override
    public Optional<RuleResult> evaluate(FraudContext ctx, BankSettings s) {
        int countIncludingThis = ctx.recentOutgoingCount() + 1;
        if (countIncludingThis < s.r2Count()) {
            return Optional.empty();
        }
        return Optional.of(new RuleResult(id(), s.r2Points(),
                countIncludingThis + " transactions in " + s.r2WindowMinutes() + " minutes"));
    }
}
