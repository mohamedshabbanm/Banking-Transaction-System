package com.ershak.fraud;

import com.ershak.fraud.rules.DrainAccountRule;
import com.ershak.fraud.rules.LateNightRule;
import com.ershak.fraud.rules.NearDailyLimitRule;
import com.ershak.fraud.rules.NewRecipientRule;
import com.ershak.fraud.rules.RapidFireRule;
import com.ershak.fraud.rules.UnusualAmountRule;
import com.ershak.service.BankSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "Sedo w estareya7": the Fraud Guard.
 * Runs every rule over a transaction, adds up the points and decides ALLOW, OTP or HOLD.
 */
public class FraudEngine {

    private final List<FraudRule> rules;

    public FraudEngine() {
        this(List.of(
                new UnusualAmountRule(),
                new RapidFireRule(),
                new NewRecipientRule(),
                new LateNightRule(),
                new DrainAccountRule(),
                new NearDailyLimitRule()));
    }

    public FraudEngine(List<FraudRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public RiskAssessment assess(FraudContext context, BankSettings settings) {
        List<RuleResult> triggered = new ArrayList<>();
        int score = 0;
        for (FraudRule rule : rules) {
            Optional<RuleResult> result = rule.evaluate(context, settings);
            if (result.isPresent()) {
                triggered.add(result.get());
                score += result.get().points();
            }
        }
        score = Math.min(score, 100);
        return new RiskAssessment(score, List.copyOf(triggered), decide(score, settings));
    }

    static RiskDecision decide(int score, BankSettings settings) {
        if (score >= settings.holdThreshold()) {
            return RiskDecision.HOLD;
        }
        if (score >= settings.otpThreshold()) {
            return RiskDecision.OTP;
        }
        return RiskDecision.ALLOW;
    }

    public List<FraudRule> getRules() {
        return rules;
    }
}
