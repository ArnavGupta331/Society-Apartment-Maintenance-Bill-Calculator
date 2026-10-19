package com.society.maintenance.service;

import com.society.maintenance.exception.InvalidInputException;

import java.math.BigDecimal;
import java.util.Properties;

/** Billing rules, loaded from application.properties. */
public record BillingConfig(String societyName, BigDecimal ratePerSqFt, BigDecimal waterCharge, BigDecimal sinkingFund,
                            int dueDayOfMonth, int graceDays, BigDecimal lateFeePerDay, BigDecimal lateFeeCapPercent) {

    public BillingConfig {
        if (ratePerSqFt.signum() < 0 || waterCharge.signum() < 0 || sinkingFund.signum() < 0 || lateFeePerDay.signum() < 0) {
            throw new InvalidInputException("Billing amounts cannot be negative");
        }
        if (dueDayOfMonth < 1 || dueDayOfMonth > 31) {
            throw new InvalidInputException("Due day must be between 1 and 31");
        }
        if (graceDays < 0) {
            throw new InvalidInputException("Grace days cannot be negative");
        }
        if (lateFeeCapPercent.signum() < 0 || lateFeeCapPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new InvalidInputException("Late fee cap must be between 0 and 100 percent");
        }
    }

    public static BillingConfig from(Properties p) {
        return new BillingConfig(
                p.getProperty("society.name", "My Apartment Society"),
                new BigDecimal(p.getProperty("billing.rate.per.sqft", "2.50")),
                new BigDecimal(p.getProperty("billing.water.charge", "200.00")),
                new BigDecimal(p.getProperty("billing.sinking.fund", "500.00")),
                Integer.parseInt(p.getProperty("billing.due.day", "10")),
                Integer.parseInt(p.getProperty("billing.grace.days", "5")),
                new BigDecimal(p.getProperty("billing.late.fee.per.day", "10.00")),
                new BigDecimal(p.getProperty("billing.late.fee.cap.percent", "25")));
    }
}
