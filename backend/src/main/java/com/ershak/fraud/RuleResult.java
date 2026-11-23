package com.ershak.fraud;

/** One rule that fired, how many points it added, and a plain-language reason the customer can read. */
public record RuleResult(String ruleId, int points, String reason) {

    /** Compact storage form: "R1|30|reason". */
    public String encode() {
        return ruleId + "|" + points + "|" + reason.replace("|", "/").replace("\n", " ");
    }

    public static RuleResult decode(String line) {
        String[] parts = line.split("\\|", 3);
        return new RuleResult(parts[0], Integer.parseInt(parts[1]), parts.length > 2 ? parts[2] : "");
    }
}
