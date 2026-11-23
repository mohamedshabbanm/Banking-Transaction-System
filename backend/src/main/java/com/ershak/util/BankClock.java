package com.ershak.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Single source of "now" for the whole system, in Cairo time.
 * Tests replace the clock to simulate night-time transfers or expired OTPs.
 */
public final class BankClock {

    public static final ZoneId ZONE = ZoneId.of("Africa/Cairo");
    private static Clock clock = Clock.system(ZONE);

    private BankClock() {
    }

    public static long nowMillis() {
        return clock.millis();
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public static ZonedDateTime toZoned(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZONE);
    }

    /** Epoch millis at 00:00 Cairo time today. */
    public static long startOfToday() {
        return now().toLocalDate().atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    /** Epoch millis at 00:00 Cairo time on the 1st of this month. */
    public static long startOfMonth() {
        return now().toLocalDate().withDayOfMonth(1).atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    public static void useClock(Clock testClock) {
        clock = testClock;
    }

    public static void reset() {
        clock = Clock.system(ZONE);
    }
}
