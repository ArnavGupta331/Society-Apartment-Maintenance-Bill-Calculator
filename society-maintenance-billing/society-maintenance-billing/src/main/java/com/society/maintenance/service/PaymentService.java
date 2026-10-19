package com.society.maintenance.service;

import com.society.maintenance.exception.BillNotFoundException;
import com.society.maintenance.exception.InvalidInputException;
import com.society.maintenance.exception.PaymentException;
import com.society.maintenance.model.Bill;
import com.society.maintenance.model.BillStatus;
import com.society.maintenance.model.Payment;
import com.society.maintenance.model.PaymentMode;
import com.society.maintenance.repository.BillRepository;
import com.society.maintenance.repository.PaymentRepository;
import com.society.maintenance.util.Database;
import com.society.maintenance.util.Money;
import com.society.maintenance.util.Validator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PaymentService {
    private static final DateTimeFormatter RECEIPT_MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Database db;
    private final BillRepository bills;
    private final PaymentRepository payments;
    private final BillingCalculator calculator;

    public PaymentService(Database db, BillRepository bills, PaymentRepository payments, BillingCalculator calculator) {
        this.db = db;
        this.bills = bills;
        this.payments = payments;
        this.calculator = calculator;
    }

    /**
     * Records a (possibly partial) payment. The late fee is calculated as of the payment date and stored
     * on the bill; the payment insert and the bill update happen in a single transaction.
     */
    public Payment recordPayment(long billId, BigDecimal amount, PaymentMode mode, LocalDate paidOn) {
        Validator.positive(amount, "Payment amount");
        if (mode == null || paidOn == null) {
            throw new InvalidInputException("Payment mode and date are required");
        }
        Bill bill = bills.findById(billId).orElseThrow(() -> new BillNotFoundException(billId));
        if (bill.status() == BillStatus.PAID) {
            throw new PaymentException("Bill " + billId + " is already fully paid");
        }

        BigDecimal paySoFar = payments.totalPaid(billId);
        BigDecimal lateFee = calculator.effectiveLateFee(bill, paidOn);
        BigDecimal totalDue = bill.principal().add(lateFee);
        BigDecimal outstanding = totalDue.subtract(paySoFar);
        BigDecimal paying = Money.scale(amount);

        if (paying.compareTo(outstanding) > 0) {
            throw new PaymentException("Payment of " + Money.format(paying) + " exceeds the outstanding "
                    + Money.format(outstanding) + " for bill " + billId);
        }

        BillStatus newStatus = calculator.statusFor(totalDue, paySoFar.add(paying));
        String receiptNo = "RCPT-" + bill.billMonth().format(RECEIPT_MONTH) + "-" + billId + "-"
                + (payments.findByBillId(billId).size() + 1);
        Bill updated = bill.withLateFeeAndStatus(lateFee, newStatus);
        Payment toSave = new Payment(0, billId, paying, paidOn, mode, receiptNo);

        return db.inTransaction(c -> {
            bills.update(c, updated);
            return payments.save(c, toSave);
        });
    }

    public BigDecimal totalPaid(long billId) {
        return payments.totalPaid(billId);
    }
}
