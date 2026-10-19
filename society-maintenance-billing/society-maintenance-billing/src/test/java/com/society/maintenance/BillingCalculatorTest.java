package com.society.maintenance;

import com.society.maintenance.model.Bill;
import com.society.maintenance.model.BillStatus;
import com.society.maintenance.model.Flat;
import com.society.maintenance.service.BillingCalculator;
import com.society.maintenance.service.BillingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BillingCalculatorTest {

    private BillingCalculator calc;
    private final Flat flat = new Flat(1, "A-101", "Ravi Kumar", "9876543210", new BigDecimal("1000"));

    static BillingConfig config(int dueDay) {
        return new BillingConfig("Test Society", new BigDecimal("2.50"), new BigDecimal("200"), new BigDecimal("500"),
                dueDay, 5, new BigDecimal("10"), new BigDecimal("25"));
    }

    static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @BeforeEach
    void setUp() {
        calc = new BillingCalculator(config(10));
    }

    @Test
    void maintenanceIsAreaTimesRate() {
        assertMoney("2500.00", calc.maintenanceFor(new BigDecimal("1000")));
        assertMoney("2937.50", calc.maintenanceFor(new BigDecimal("1175")));
    }

    @Test
    void buildBillContainsAllChargesAndStartsPending() {
        Bill bill = calc.buildBill(flat, YearMonth.of(2025, 1));
        assertMoney("2500.00", bill.maintenance());
        assertMoney("3200.00", bill.principal());
        assertMoney("0", bill.lateFee());
        assertEquals(LocalDate.of(2025, 1, 10), bill.dueDate());
        assertEquals(BillStatus.PENDING, bill.status());
    }

    @Test
    void dueDateIsClampedToLastDayOfShortMonths() {
        BillingCalculator day31 = new BillingCalculator(config(31));
        assertEquals(LocalDate.of(2025, 2, 28), day31.dueDateFor(YearMonth.of(2025, 2)));
        assertEquals(LocalDate.of(2024, 2, 29), day31.dueDateFor(YearMonth.of(2024, 2)));
        assertEquals(LocalDate.of(2025, 1, 31), day31.dueDateFor(YearMonth.of(2025, 1)));
    }

    @Test
    void noLateFeeOnOrBeforeDueDateOrInsideGracePeriod() {
        BigDecimal p = new BigDecimal("3200.00");
        LocalDate due = LocalDate.of(2025, 1, 10);
        assertMoney("0", calc.lateFee(p, due, due.minusDays(3)));
        assertMoney("0", calc.lateFee(p, due, due));
        assertMoney("0", calc.lateFee(p, due, due.plusDays(5))); // last day of grace
    }

    @Test
    void lateFeeStartsOneDayAfterGraceAndGrowsPerDay() {
        BigDecimal p = new BigDecimal("3200.00");
        LocalDate due = LocalDate.of(2025, 1, 10);
        assertMoney("10.00", calc.lateFee(p, due, due.plusDays(6)));
        assertMoney("100.00", calc.lateFee(p, due, due.plusDays(15)));
    }

    @Test
    void lateFeeIsCappedAtPercentageOfCharges() {
        BigDecimal p = new BigDecimal("3200.00"); // cap = 25% = 800
        LocalDate due = LocalDate.of(2025, 1, 10);
        assertMoney("800.00", calc.lateFee(p, due, due.plusDays(5 + 80)));
        assertMoney("800.00", calc.lateFee(p, due, due.plusDays(400)));
    }

    @Test
    void paidBillsKeepTheirFrozenLateFee() {
        Bill paid = calc.buildBill(flat, YearMonth.of(2025, 1))
                .withLateFeeAndStatus(new BigDecimal("50.00"), BillStatus.PAID);
        assertMoney("50.00", calc.effectiveLateFee(paid, LocalDate.of(2026, 1, 1)));
    }

    @Test
    void unpaidBillsAccrueLateFeeWithTime() {
        Bill bill = calc.buildBill(flat, YearMonth.of(2025, 1));
        assertMoney("0", calc.effectiveLateFee(bill, LocalDate.of(2025, 1, 12)));
        assertMoney("150.00", calc.effectiveLateFee(bill, LocalDate.of(2025, 1, 30)));
    }

    @Test
    void statusDependsOnAmountPaid() {
        BigDecimal due = new BigDecimal("3200.00");
        assertEquals(BillStatus.PENDING, calc.statusFor(due, BigDecimal.ZERO));
        assertEquals(BillStatus.PARTIAL, calc.statusFor(due, new BigDecimal("1000")));
        assertEquals(BillStatus.PAID, calc.statusFor(due, new BigDecimal("3200.00")));
    }

    @Test
    void configRejectsInvalidRules() {
        assertThrows(RuntimeException.class, () -> new BillingConfig("x", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                0, 5, BigDecimal.TEN, BigDecimal.TEN));
        assertThrows(RuntimeException.class, () -> new BillingConfig("x", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                10, 5, BigDecimal.TEN, new BigDecimal("150")));
    }
}
