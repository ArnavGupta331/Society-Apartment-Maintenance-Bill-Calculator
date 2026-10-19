package com.society.maintenance.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class Money {
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private Money() { }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static String format(BigDecimal value) {
        return String.format(Locale.US, "Rs. %,.2f", value);
    }
}
