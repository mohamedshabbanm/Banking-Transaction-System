package com.ershak.util;

import com.ershak.exception.ValidationException;

import java.util.regex.Pattern;

/** Input validation used by the services before anything touches the database. */
public final class Validators {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9._]{3,30}$");
    private static final Pattern PHONE = Pattern.compile("^01[0125][0-9]{8}$");

    private Validators() {
    }

    public static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " is required.");
        }
        return value.trim();
    }

    public static String email(String value) {
        String v = required(value, "Email").toLowerCase();
        if (!EMAIL.matcher(v).matches()) {
            throw new ValidationException("Enter a valid email address.");
        }
        return v;
    }

    public static String username(String value) {
        String v = required(value, "Username").toLowerCase();
        if (!USERNAME.matcher(v).matches()) {
            throw new ValidationException("Username must be 3-30 letters, numbers, dots or underscores.");
        }
        return v;
    }

    /** Egyptian mobile numbers: 010, 011, 012 or 015 followed by 8 digits. */
    public static String phone(String value) {
        String v = required(value, "Phone").replace(" ", "");
        if (!PHONE.matcher(v).matches()) {
            throw new ValidationException("Enter a valid Egyptian mobile number, like 01012345678.");
        }
        return v;
    }

    /** At least 8 characters with an upper-case letter, a lower-case letter and a digit. */
    public static String password(String value) {
        if (value == null || value.length() < 8
                || !value.chars().anyMatch(Character::isUpperCase)
                || !value.chars().anyMatch(Character::isLowerCase)
                || !value.chars().anyMatch(Character::isDigit)) {
            throw new ValidationException("WEAK_PASSWORD",
                    "Password needs at least 8 characters, with upper case, lower case and a number.");
        }
        return value;
    }
}
