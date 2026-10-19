package com.society.maintenance;

import com.society.maintenance.exception.BillNotFoundException;
import com.society.maintenance.exception.DuplicateBillException;
import com.society.maintenance.exception.DuplicateFlatException;
import com.society.maintenance.exception.FlatNotFoundException;
import com.society.maintenance.exception.InvalidInputException;
import com.society.maintenance.exception.PaymentException;
import com.society.maintenance.model.Bill;
import com.society.maintenance.model.BillStatus;
import com.society.maintenance.model.Payment;
import com.society.maintenance.model.PaymentMode;
import com.society.maintenance.repository.BillRepository;
import com.society.maintenance.repository.FlatRepository;
import com.society.maintenance.repository.PaymentRepository;
import com.society.maintenance.service.BillingCalculator;
import com.society.maintenance.service.BillingService;
import com.society.maintenance.service.FlatService;
import com.society.maintenance.service.PaymentService;
import com.society.maintenance.service.ReceiptService;
import com.society.maintenance.service.ReportService;
import com.society.maintenance.util.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static com.society.maintenance.BillingCalculatorTest.assertMoney;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests of the service + repository layers against a real (temporary) SQLite database. */
class MaintenanceWorkflowTest {

    private static final YearMonth JAN = YearMonth.of(2025, 1);

    @TempDir
    Path tmp;

    private FlatService flats;
    private BillingService billing;
    private PaymentService payments;
    private ReportService reports;
    private ReceiptService receipts;
    private BillRepository billRepo;

    @BeforeEach
    void setUp() {
        Database db = Database.forFile(tmp.resolve("test.db"));
        FlatRepository flatRepo = new FlatRepository(db);
        billRepo = new BillRepository(db);
        PaymentRepository paymentRepo = new PaymentRepository(db);
        BillingCalculator calc = new BillingCalculator(BillingCalculatorTest.config(10));

        flats = new FlatService(flatRepo);
        billing = new BillingService(flatRepo, billRepo, calc);
        payments = new PaymentService(db, billRepo, paymentRepo, calc);
        reports = new ReportService(flatRepo, billRepo, paymentRepo, calc);
        receipts = new ReceiptService(flatRepo, billRepo, paymentRepo, calc);

        flats.addFlat("A-101", "Ravi Kumar", "9876543210", "1000");   // principal 3200.00
        flats.addFlat("B-202", "Meena Iyer", "9876543213", "1500");   // principal 4450.00
    }

    private Bill billOf(String flatNumber) {
        return billRepo.findByFlatAndMonth(flats.getByNumber(flatNumber).id(), JAN).orElseThrow();
    }

    @Test
    void duplicateAndUnknownFlatsAreRejected() {
        assertThrows(DuplicateFlatException.class, () -> flats.addFlat("a-101", "Someone Else", "9876543299", "900"));
        assertThrows(FlatNotFoundException.class, () -> flats.getByNumber("Z-999"));
        assertThrows(InvalidInputException.class, () -> flats.addFlat("C-1", "X", "9876543299", "900"));
    }

    @Test
    void generatesOneBillPerFlatAndRefusesToDuplicateAMonth() {
        List<Bill> created = billing.generateMonthlyBills(JAN);
        assertEquals(2, created.size());
        assertThrows(DuplicateBillException.class, () -> billing.generateMonthlyBills(JAN));

        flats.addFlat("C-303", "Karthik Raja", "9876543214", "800");
        assertEquals(1, billing.generateMonthlyBills(JAN).size()); // only the new flat
    }

    @Test
    void onTimeFullPaymentMarksBillPaidWithoutLateFee() {
        billing.generateMonthlyBills(JAN);
        Bill bill = billOf("A-101");
        Payment p = payments.recordPayment(bill.id(), new BigDecimal("3200"), PaymentMode.UPI, LocalDate.of(2025, 1, 8));

        assertEquals("RCPT-202501-" + bill.id() + "-1", p.receiptNo());
        Bill after = billOf("A-101");
        assertEquals(BillStatus.PAID, after.status());
        assertMoney("0", after.lateFee());
    }

    @Test
    void partialThenLatePaymentAddsLateFeeAndClearsTheBill() {
        billing.generateMonthlyBills(JAN);
        Bill bill = billOf("B-202");

        payments.recordPayment(bill.id(), new BigDecimal("1000"), PaymentMode.CASH, LocalDate.of(2025, 1, 9));
        assertEquals(BillStatus.PARTIAL, billOf("B-202").status());

        // 26 Jan = 16 days after due date, 11 chargeable days after grace => 110.00 late fee
        List<ReportService.DueRow> rows = reports.pendingDues(LocalDate.of(2025, 1, 26));
        assertEquals(2, rows.size());
        ReportService.DueRow b202 = rows.stream().filter(r -> r.flat().flatNumber().equals("B-202")).findFirst().orElseThrow();
        assertMoney("110.00", b202.lateFee());
        assertMoney("3560.00", b202.outstanding());

        payments.recordPayment(bill.id(), new BigDecimal("3560"), PaymentMode.BANK_TRANSFER, LocalDate.of(2025, 1, 26));
        Bill after = billOf("B-202");
        assertEquals(BillStatus.PAID, after.status());
        assertMoney("110.00", after.lateFee());
        assertMoney("0", billRepo.findById(bill.id()).orElseThrow().totalDue().subtract(new BigDecimal("4560.00")));
    }

    @Test
    void overpaymentPaymentOnPaidBillAndUnknownBillAreRejected() {
        billing.generateMonthlyBills(JAN);
        Bill bill = billOf("A-101");
        LocalDate day = LocalDate.of(2025, 1, 8);

        assertThrows(PaymentException.class, () -> payments.recordPayment(bill.id(), new BigDecimal("99999"), PaymentMode.CASH, day));
        assertThrows(InvalidInputException.class, () -> payments.recordPayment(bill.id(), new BigDecimal("-5"), PaymentMode.CASH, day));
        assertThrows(BillNotFoundException.class, () -> payments.recordPayment(9999, BigDecimal.TEN, PaymentMode.CASH, day));

        payments.recordPayment(bill.id(), new BigDecimal("3200"), PaymentMode.CASH, day);
        assertThrows(PaymentException.class, () -> payments.recordPayment(bill.id(), BigDecimal.TEN, PaymentMode.CASH, day));
        // failed attempts must not have written anything
        assertMoney("3200.00", payments.totalPaid(bill.id()));
    }

    @Test
    void pendingReportOnlyListsUnpaidBillsAndSummaryAddsUp() {
        billing.generateMonthlyBills(JAN);
        Bill a = billOf("A-101");
        payments.recordPayment(a.id(), new BigDecimal("3200"), PaymentMode.UPI, LocalDate.of(2025, 1, 8));

        LocalDate asOf = LocalDate.of(2025, 1, 12); // inside grace period, so no late fees yet
        List<ReportService.DueRow> rows = reports.pendingDues(asOf);
        assertEquals(1, rows.size());
        assertEquals("B-202", rows.get(0).flat().flatNumber());
        assertMoney("4450.00", rows.get(0).outstanding());

        ReportService.CollectionSummary s = reports.collectionSummary(JAN, asOf);
        assertEquals(2, s.billsGenerated());
        assertEquals(1, s.billsPaid());
        assertMoney("7650.00", s.billed());
        assertMoney("3200.00", s.collected());
        assertMoney("4450.00", s.pending());
        assertTrue(ReportService.formatPendingDues(rows, asOf).contains("B-202"));
    }

    @Test
    void generatesAPdfReceipt() throws Exception {
        billing.generateMonthlyBills(JAN);
        Bill bill = billOf("A-101");
        Payment p = payments.recordPayment(bill.id(), new BigDecimal("3200"), PaymentMode.UPI, LocalDate.of(2025, 1, 8));

        Path pdf = receipts.generate(p.receiptNo(), tmp.resolve("receipts"));
        assertTrue(Files.exists(pdf));
        byte[] head = Files.readAllBytes(pdf);
        assertEquals("%PDF", new String(head, 0, 4));
    }
}
