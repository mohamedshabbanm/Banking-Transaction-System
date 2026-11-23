package com.ershak.fraud;

import java.util.List;

/** The Fraud Guard's full answer: total score, the rules that fired and the decision. */
public record RiskAssessment(int score, List<RuleResult> triggered, RiskDecision decision) {

    public static RiskAssessment none() {
        return new RiskAssessment(0, List.of(), RiskDecision.ALLOW);
    }

    public String level() {
        return switch (decision) {
            case ALLOW -> "LOW";
            case OTP -> "MEDIUM";
            case HOLD -> "HIGH";
        };
    }
}
