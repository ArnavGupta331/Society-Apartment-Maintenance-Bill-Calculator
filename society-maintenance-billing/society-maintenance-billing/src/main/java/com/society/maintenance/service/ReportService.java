package com.society.maintenance.service;

import com.society.maintenance.model.Bill;
import com.society.maintenance.model.Flat;
import com.society.maintenance.model.Payment;
import com.society.maintenance.repository.BillRepository;
import com.society.maintenance.repository.FlatRepository;
import com.society.maintenance.repository.PaymentRepository;
import com.society.maintenance.util.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportService {

    public record DueRow(Bill bill, Flat flat, BigDecimal lateFee, BigDecimal paid, BigDecimal outstanding, long daysOverdue) { }

    public record CollectionSummary(YearMonth month, int billsGenerated, int billsPaid,
                                    BigDecimal billed, BigDecimal collected, BigDecimal pending) { }

    private final FlatRepository flats;
    private final BillRepository bills;
    private final PaymentRepository payments;
    private final BillingCalculator calculator;

    public ReportService(FlatRepository flats, BillRepository bills, PaymentRepository payments, BillingCalculator calculator) {
        this.flats = flats;
        this.bills = bills;
        this.payments = payments;
        this.calculator = calculator;
    }

    /** Every unpaid or partly paid bill with its late fee calculated as of the given date. */
    public List<DueRow> pendingDues(LocalDate asOf) {
        Map<Long, Flat> flatById = new HashMap<>();
        flats.findAll().forEach(f -> flatById.put(f.id(), f));

        List<DueRow> rows = new ArrayList<>();
        for (Bill bill : bills.findOutstanding()) {
            BigDecimal lateFee = calculator.effectiveLateFee(bill, asOf);
            BigDecimal paid = payments.totalPaid(bill.id());
            BigDecimal outstanding = bill.principal().add(lateFee).subtract(paid);
            long overdue = Math.max(0, ChronoUnit.DAYS.between(bill.dueDate(), asOf));
            rows.add(new DueRow(bill, flatById.get(bill.flatId()), lateFee, paid, outstanding, overdue));
        }
        rows.sort(Comparator.comparing((DueRow r) -> r.flat().flatNumber()).thenComparing(r -> r.bill().billMonth()));
        return rows;
    }

    public CollectionSummary collectionSummary(YearMonth month, LocalDate asOf) {
        List<Bill> monthBills = bills.findByMonth(month);
        BigDecimal billed = Money.ZERO;
        BigDecimal collected = Money.ZERO;
        int paidCount = 0;
        for (Bill bill : monthBills) {
            billed = billed.add(bill.principal().add(calculator.effectiveLateFee(bill, asOf)));
            for (Payment p : payments.findByBillId(bill.id())) collected = collected.add(p.amount());
            if (bill.status() == com.society.maintenance.model.BillStatus.PAID) paidCount++;
        }
        return new CollectionSummary(month, monthBills.size(), paidCount, billed, collected, billed.subtract(collected));
    }

    public static String formatPendingDues(List<DueRow> rows, LocalDate asOf) {
        if (rows.isEmpty()) {
            return "No pending dues as of " + asOf + ". All bills are cleared.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("PENDING DUES REPORT (as of %s)%n", asOf));
        sb.append(String.format("%-8s %-18s %-8s %-7s %12s %10s %12s %12s %5s%n",
                "Flat", "Owner", "Month", "BillId", "Charges", "LateFee", "Paid", "Outstanding", "Days"));
        sb.append("-".repeat(104)).append(System.lineSeparator());
        BigDecimal total = Money.ZERO;
        for (DueRow r : rows) {
            String owner = r.flat().ownerName();
            if (owner.length() > 18) owner = owner.substring(0, 17) + ".";
            sb.append(String.format("%-8s %-18s %-8s %-7d %,12.2f %,10.2f %,12.2f %,12.2f %5d%n",
                    r.flat().flatNumber(), owner, r.bill().billMonth(), r.bill().id(), r.bill().principal(),
                    r.lateFee(), r.paid(), r.outstanding(), r.daysOverdue()));
            total = total.add(r.outstanding());
        }
        sb.append("-".repeat(104)).append(System.lineSeparator());
        sb.append(String.format("Total outstanding: %s across %d bill(s)%n", Money.format(total), rows.size()));
        return sb.toString();
    }
}
