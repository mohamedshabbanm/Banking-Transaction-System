package com.ershak.security;

import com.ershak.exception.BankException;
import com.ershak.exception.ValidationException;
import com.ershak.util.Validators;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityTest {

    @Test
    @DisplayName("Same password, different salt, different hash; verify works")
    void passwordHashing() {
        String s1 = PasswordHasher.newSalt();
        String s2 = PasswordHasher.newSalt();
        String h1 = PasswordHasher.hash("Secret123", s1);
        assertNotEquals(h1, PasswordHasher.hash("Secret123", s2));
        assertTrue(PasswordHasher.verify("Secret123", s1, h1));
        assertFalse(PasswordHasher.verify("secret123", s1, h1));
    }

    @Test
    @DisplayName("Password policy: 8+ chars with upper, lower and digit")
    void passwordPolicy() {
        assertEquals("Ershak2026", Validators.password("Ershak2026"));
        assertThrows(ValidationException.class, () -> Validators.password("short1A"));
        assertThrows(ValidationException.class, () -> Validators.password("alllowercase1"));
        assertThrows(ValidationException.class, () -> Validators.password("NoDigitsHere"));
    }

    @Test
    @DisplayName("Egyptian phone and email validation")
    void contactValidation() {
        assertEquals("01012345678", Validators.phone("010 1234 5678"));
        assertThrows(ValidationException.class, () -> Validators.phone("0131234567"));
        assertThrows(ValidationException.class, () -> Validators.email("not-an-email"));
    }

    @Test
    @DisplayName("Rate limiter blocks the request after the limit")
    void rateLimiter() {
        RateLimiter limiter = new RateLimiter(3, 60_000);
        limiter.check("1.2.3.4", 0);
        limiter.check("1.2.3.4", 1);
        limiter.check("1.2.3.4", 2);
        assertThrows(BankException.class, () -> limiter.check("1.2.3.4", 3));
        limiter.check("5.6.7.8", 3);
        limiter.check("1.2.3.4", 70_000);
    }
}
