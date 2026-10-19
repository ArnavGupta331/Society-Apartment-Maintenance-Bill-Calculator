package com.society.maintenance.model;

import com.society.maintenance.exception.InvalidInputException;

public enum PaymentMode {
    CASH, UPI, CARD, CHEQUE, BANK_TRANSFER;

    public static PaymentMode parse(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidInputException("Payment mode is required (CASH, UPI, CARD, CHEQUE, BANK_TRANSFER)");
        }
        try {
            return valueOf(text.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Unknown payment mode '" + text + "'. Use CASH, UPI, CARD, CHEQUE or BANK_TRANSFER");
        }
    }
}
