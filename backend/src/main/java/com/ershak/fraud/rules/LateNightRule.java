package com.ershak.fraud.rules;

import com.ershak.fraud.FraudContext;
import com.ershak.fraud.FraudRule;
import com.ershak.fraud.RuleResult;
import com.ershak.service.BankSettings;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/** R4: a sizeable amount sent in the middle of the night. */
public class LateNightRule implements FraudRule {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    @Override
    public String id() {
        return "R4";
    }

    @Override
    public String description() {
        return "Large amount sent late at night";
    }

    @Override
    public Optional<RuleResult> evaluate(FraudContext ctx, BankSettings s) {
        int hour = ctx.time().getHour();
        boolean night = hour >= s.r4StartHour() && hour < s.r4EndHour();
        if (!night || ctx.amount().compareTo(s.r4Amount()) < 0) {
            return Optional.empty();
        }
        return Optional.of(new RuleResult(id(), s.r4Points(), "Sent at " + ctx.time().format(TIME)));
    }
}
