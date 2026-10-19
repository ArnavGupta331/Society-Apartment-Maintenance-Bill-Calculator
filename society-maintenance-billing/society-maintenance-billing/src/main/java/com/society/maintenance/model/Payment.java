package com.society.maintenance.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Payment(long id, long billId, BigDecimal amount, LocalDate paidOn, PaymentMode mode, String receiptNo) {

    public Payment withId(long newId) {
        return new Payment(newId, billId, amount, paidOn, mode, receiptNo);
    }
}
