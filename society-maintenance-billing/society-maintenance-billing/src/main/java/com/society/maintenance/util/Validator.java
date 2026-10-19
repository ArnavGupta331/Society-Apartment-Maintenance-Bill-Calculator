package com.society.maintenance.util;

import com.society.maintenance.exception.InvalidInputException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Central input validation. Every method either returns a cleaned value or throws InvalidInputException. */
public final class Validator {
    private static final Pattern FLAT_NUMBER = Pattern.compile("^[A-Za-z0-9-]{1,10}$");
    private static final Pattern PHONE = Pattern.compile("^[6-9][0-9]{9}$");
    private static final Pattern PERSON_NAME = Pattern.compile("^[\\p{L} .'-]{2,60}$");

    private Validator() { }

    public static String flatNumber(String value) {
        String v = requireNonBlank(value, "Flat number").toUpperCase();
        if (!FLAT_NUMBER.matcher(v).matches()) {
            throw new InvalidInputException("Flat number must be 1-10 letters, digits or hyphens (e.g. A-101)");
        }
        return v;
    }

    public static String personName(String value) {
        String v = requireNonBlank(value, "Owner name");
        if (!PERSON_NAME.matcher(v).matches()) {
            throw new InvalidInputException("Owner name must be 2-60 characters (letters, spaces, . ' -)");
        }
        return v;
    }

    public static String phone(String value) {
        String v = requireNonBlank(value, "Phone number");
        if (!PHONE.matcher(v).matches()) {
            throw new InvalidInputException("Phone must be a 10-digit Indian mobile number starting with 6-9");
        }
        return v;
    }

    public static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(field + " is required");
        }
        return value.trim();
    }

    /** Parses a strictly positive decimal with at most 2 decimal places. */
    public static BigDecimal positiveDecimal(String text, String field) {
        BigDecimal value;
        try {
            value = new BigDecimal(requireNonBlank(text, field));
        } catch (NumberFormatException e) {
            throw new InvalidInputException(field + " must be a number");
        }
        return positive(value, field);
    }

    public static BigDecimal positive(BigDecimal value, String field) {
        if (value == null) {
            throw new InvalidInputException(field + " is required");
        }
        if (value.signum() <= 0) {
            throw new InvalidInputException(field + " must be greater than zero");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new InvalidInputException(field + " can have at most 2 decimal places");
        }
        return Money.scale(value);
    }

    public static YearMonth month(String text) {
        try {
            return YearMonth.parse(requireNonBlank(text, "Month"));
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("Month must be in yyyy-MM format (e.g. 2025-01)");
        }
    }

    public static LocalDate date(String text) {
        try {
            return LocalDate.parse(requireNonBlank(text, "Date"));
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("Date must be in yyyy-MM-dd format (e.g. 2025-01-15)");
        }
    }

    public static long id(String text, String field) {
        try {
            long v = Long.parseLong(requireNonBlank(text, field));
            if (v <= 0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            throw new InvalidInputException(field + " must be a positive whole number");
        }
    }
}
