package com.society.maintenance.service;

import com.society.maintenance.model.Bill;
import com.society.maintenance.model.BillStatus;
import com.society.maintenance.model.Flat;
import com.society.maintenance.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * Pure billing rules: no database, no I/O, so it is trivial to unit test.
 *
 * <p>Late fee rule: nothing is charged until the grace period after the due date is over.
 * After that, a fixed amount per late day is charged, capped at a percentage of the bill.
 */
public class BillingCalculator {
    private final BillingConfig config;

    public BillingCalculator(BillingConfig config) {
        this.config = config;
    }

    public BillingConfig config() {
        return config;
    }

    /** Maintenance = built-up area x rate per sq ft. */
    public BigDecimal maintenanceFor(BigDecimal areaSqFt) {
        return Money.scale(areaSqFt.multiply(config.ratePerSqFt()));
    }

    /** Due date is the configured day of the month, clamped to the month's last day (e.g. 31 in February). */
    public LocalDate dueDateFor(YearMonth month) {
        return month.atDay(Math.min(config.dueDayOfMonth(), month.lengthOfMonth()));
    }

    public Bill buildBill(Flat flat, YearMonth month) {
        return new Bill(0, flat.id(), month, maintenanceFor(flat.areaSqFt()), Money.scale(config.waterCharge()),
                Money.scale(config.sinkingFund()), Money.ZERO, dueDateFor(month), BillStatus.PENDING);
    }

    public BigDecimal lateFee(BigDecimal principal, LocalDate dueDate, LocalDate asOf) {
        long chargeableDays = ChronoUnit.DAYS.between(dueDate, asOf) - config.graceDays();
        if (chargeableDays <= 0) {
            return Money.ZERO;
        }
        BigDecimal fee = config.lateFeePerDay().multiply(BigDecimal.valueOf(chargeableDays));
        BigDecimal cap = principal.multiply(config.lateFeeCapPercent()).divide(BigDecimal.valueOf(100));
        return Money.scale(fee.min(cap));
    }

    /** Late fee that applies to a bill as of a date. Paid bills keep the fee frozen at payment time. */
    public BigDecimal effectiveLateFee(Bill bill, LocalDate asOf) {
        if (bill.status() == BillStatus.PAID) {
            return bill.lateFee();
        }
        return bill.lateFee().max(lateFee(bill.principal(), bill.dueDate(), asOf));
    }

    public BillStatus statusFor(BigDecimal totalDue, BigDecimal totalPaid) {
        if (totalPaid.signum() <= 0) return BillStatus.PENDING;
        return totalPaid.compareTo(totalDue) >= 0 ? BillStatus.PAID : BillStatus.PARTIAL;
    }
}
