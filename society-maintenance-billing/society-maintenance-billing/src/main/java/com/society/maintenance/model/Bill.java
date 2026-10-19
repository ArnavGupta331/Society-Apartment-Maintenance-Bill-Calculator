package com.society.maintenance.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** A monthly maintenance bill. The late fee is stored once a payment freezes it. */
public record Bill(long id, long flatId, YearMonth billMonth, BigDecimal maintenance, BigDecimal water,
                   BigDecimal sinkingFund, BigDecimal lateFee, LocalDate dueDate, BillStatus status) {

    /** Charges before any late fee. */
    public BigDecimal principal() {
        return maintenance.add(water).add(sinkingFund);
    }

    public BigDecimal totalDue() {
        return principal().add(lateFee);
    }

    public Bill withId(long newId) {
        return new Bill(newId, flatId, billMonth, maintenance, water, sinkingFund, lateFee, dueDate, status);
    }

    public Bill withLateFeeAndStatus(BigDecimal newLateFee, BillStatus newStatus) {
        return new Bill(id, flatId, billMonth, maintenance, water, sinkingFund, newLateFee, dueDate, newStatus);
    }
}
