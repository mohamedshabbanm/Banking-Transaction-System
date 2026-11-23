package com.ershak.fraud;

import com.ershak.service.BankSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FraudEngineTest {

    private final FraudEngine engine = new FraudEngine();
    private final BankSettings settings = BankSettings.defaults();
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 23, 12, 0);
    private static final LocalDateTime TWO_AM = LocalDateTime.of(2026, 11, 23, 2, 14);

    private FraudContext ctx(String amount, String balance, LocalDateTime time, String avg, int history,
                             int recent, boolean newRecipient, String remaining) {
        return new FraudContext(new BigDecimal(amount), new BigDecimal(balance), time, new BigDecimal(avg),
                history, recent, newRecipient, true, new BigDecimal(remaining));
    }

    private List<String> ids(RiskAssessment r) {
        return r.triggered().stream().map(RuleResult::ruleId).toList();
    }

    @Test
    @DisplayName("A normal transfer is allowed with score 0")
    void normalTransfer() {
        RiskAssessment r = engine.assess(ctx("300", "10000", NOON, "500", 5, 0, false, "20000"), settings);
        assertEquals(0, r.score());
        assertEquals(RiskDecision.ALLOW, r.decision());
    }

    @Test
    @DisplayName("Unusual amount alone (30) asks for an OTP")
    void unusualAmountNeedsOtp() {
        RiskAssessment r = engine.assess(ctx("3000", "20000", NOON, "500", 4, 0, false, "20000"), settings);
        assertEquals(List.of("R1"), ids(r));
        assertEquals(RiskDecision.OTP, r.decision());
    }

    @Test
    @DisplayName("R1 needs at least 3 past transfers to judge what is usual")
    void unusualAmountNeedsHistory() {
        RiskAssessment r = engine.assess(ctx("3000", "20000", NOON, "500", 2, 0, false, "20000"), settings);
        assertTrue(!ids(r).contains("R1"));
    }

    @Test
    @DisplayName("Big amount + new recipient + 2 AM = 80, held for review")
    void classicFraudIsHeld() {
        RiskAssessment r = engine.assess(ctx("9500", "30000", TWO_AM, "500", 5, 0, true, "20000"), settings);
        assertEquals(List.of("R1", "R3", "R4"), ids(r));
        assertEquals(80, r.score());
        assertEquals(RiskDecision.HOLD, r.decision());
    }

    @Test
    @DisplayName("Third transaction within the window triggers R2")
    void rapidFire() {
        assertTrue(ids(engine.assess(ctx("100", "10000", NOON, "0", 0, 2, false, "20000"), settings)).contains("R2"));
        assertTrue(!ids(engine.assess(ctx("100", "10000", NOON, "0", 0, 1, false, "20000"), settings)).contains("R2"));
    }

    @Test
    @DisplayName("Draining the account (R5) and using most of the daily limit (R6)")
    void drainAndNearLimit() {
        RiskAssessment r = engine.assess(ctx("950", "1000", NOON, "0", 0, 0, false, "1000"), settings);
        assertEquals(List.of("R5", "R6"), ids(r));
        assertEquals(30, r.score());
    }

    @Test
    @DisplayName("Night rule ignores small amounts")
    void nightSmallAmount() {
        RiskAssessment r = engine.assess(ctx("1999", "50000", TWO_AM, "0", 0, 0, false, "20000"), settings);
        assertEquals(0, r.score());
    }

    @Test
    @DisplayName("Thresholds decide ALLOW / OTP / HOLD")
    void thresholds() {
        assertEquals(RiskDecision.ALLOW, FraudEngine.decide(29, settings));
        assertEquals(RiskDecision.OTP, FraudEngine.decide(30, settings));
        assertEquals(RiskDecision.OTP, FraudEngine.decide(59, settings));
        assertEquals(RiskDecision.HOLD, FraudEngine.decide(60, settings));
    }
}
