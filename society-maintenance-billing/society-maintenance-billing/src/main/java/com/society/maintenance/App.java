package com.society.maintenance;

import com.society.maintenance.exception.SocietyException;
import com.society.maintenance.model.Bill;
import com.society.maintenance.model.Flat;
import com.society.maintenance.model.Payment;
import com.society.maintenance.model.PaymentMode;
import com.society.maintenance.repository.BillRepository;
import com.society.maintenance.repository.FlatRepository;
import com.society.maintenance.repository.PaymentRepository;
import com.society.maintenance.service.BillingCalculator;
import com.society.maintenance.service.BillingConfig;
import com.society.maintenance.service.BillingService;
import com.society.maintenance.service.FlatService;
import com.society.maintenance.service.PaymentService;
import com.society.maintenance.service.ReceiptService;
import com.society.maintenance.service.ReportService;
import com.society.maintenance.util.Database;
import com.society.maintenance.util.Money;
import com.society.maintenance.util.Validator;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

/** Console entry point. Wires the layers together and runs the menu loop. */
public class App {
    private final Scanner in = new Scanner(System.in);
    private final FlatService flatService;
    private final BillingService billingService;
    private final PaymentService paymentService;
    private final ReportService reportService;
    private final ReceiptService receiptService;
    private final BillingCalculator calculator;
    private final BillRepository billRepository;
    private final Path receiptsDir;

    App(Properties props) {
        BillingConfig config = BillingConfig.from(props);
        Database db = Database.forFile(Path.of(props.getProperty("db.path", "society.db")));
        FlatRepository flats = new FlatRepository(db);
        billRepository = new BillRepository(db);
        PaymentRepository payments = new PaymentRepository(db);
        calculator = new BillingCalculator(config);

        flatService = new FlatService(flats);
        billingService = new BillingService(flats, billRepository, calculator);
        paymentService = new PaymentService(db, billRepository, payments, calculator);
        reportService = new ReportService(flats, billRepository, payments, calculator);
        receiptService = new ReceiptService(flats, billRepository, payments, calculator);
        receiptsDir = Path.of(props.getProperty("receipts.dir", "receipts"));
    }

    public static void main(String[] args) throws IOException {
        Properties props = new Properties();
        try (InputStream is = App.class.getResourceAsStream("/application.properties")) {
            if (is != null) props.load(is);
        }
        new App(props).run();
    }

    private void run() {
        System.out.println("=== Society Maintenance Bill Calculator ===");
        while (true) {
            printMenu();
            String choice = ask("Choose an option");
            if (choice.equals("0")) {
                System.out.println("Goodbye!");
                return;
            }
            try {
                switch (choice) {
                    case "1" -> addFlat();
                    case "2" -> listFlats();
                    case "3" -> generateBills();
                    case "4" -> viewBills();
                    case "5" -> recordPayment();
                    case "6" -> pendingDues();
                    case "7" -> collectionSummary();
                    case "8" -> receipt();
                    case "9" -> seedDemoData();
                    default -> System.out.println("Please choose a number from the menu.");
                }
            } catch (SocietyException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println("""

            1. Add flat                 6. Pending dues report
            2. List flats               7. Monthly collection summary
            3. Generate monthly bills   8. Generate PDF receipt
            4. View bills of a flat     9. Load demo data
            5. Record payment           0. Exit""");
    }

    private void addFlat() {
        Flat f = flatService.addFlat(ask("Flat number (e.g. A-101)"), ask("Owner name"),
                ask("Phone (10 digits)"), ask("Area in sq ft"));
        System.out.println("Added flat " + f.flatNumber() + " (" + f.ownerName() + ")");
    }

    private void listFlats() {
        List<Flat> flats = flatService.listAll();
        if (flats.isEmpty()) {
            System.out.println("No flats registered yet.");
            return;
        }
        System.out.printf("%-8s %-22s %-12s %10s%n", "Flat", "Owner", "Phone", "Area(sqft)");
        for (Flat f : flats) {
            System.out.printf("%-8s %-22s %-12s %10s%n", f.flatNumber(), f.ownerName(), f.phone(), f.areaSqFt());
        }
    }

    private void generateBills() {
        YearMonth month = Validator.month(ask("Month (yyyy-MM)"));
        List<Bill> created = billingService.generateMonthlyBills(month);
        System.out.println("Generated " + created.size() + " bill(s) for " + month + ", due on "
                + calculator.dueDateFor(month));
    }

    private void viewBills() {
        Flat flat = flatService.getByNumber(ask("Flat number"));
        List<Bill> bills = billingService.billsForFlat(flat);
        if (bills.isEmpty()) {
            System.out.println("No bills for " + flat.flatNumber() + " yet.");
            return;
        }
        LocalDate today = LocalDate.now();
        System.out.printf("%-7s %-8s %-11s %12s %10s %-8s%n", "BillId", "Month", "Due date", "Charges", "LateFee", "Status");
        for (Bill b : bills) {
            System.out.printf("%-7d %-8s %-11s %,12.2f %,10.2f %-8s%n", b.id(), b.billMonth(), b.dueDate(),
                    b.principal(), calculator.effectiveLateFee(b, today), b.status());
        }
    }

    private void recordPayment() {
        long billId = Validator.id(ask("Bill id"), "Bill id");
        BigDecimal amount = Validator.positiveDecimal(ask("Amount"), "Amount");
        PaymentMode mode = PaymentMode.parse(ask("Mode (CASH/UPI/CARD/CHEQUE/BANK_TRANSFER)"));
        String dateText = ask("Payment date (yyyy-MM-dd, blank = today)");
        LocalDate date = dateText.isBlank() ? LocalDate.now() : Validator.date(dateText);
        Payment p = paymentService.recordPayment(billId, amount, mode, date);
        System.out.println("Payment recorded. Receipt no: " + p.receiptNo());
    }

    private void pendingDues() {
        LocalDate today = LocalDate.now();
        System.out.println(ReportService.formatPendingDues(reportService.pendingDues(today), today));
    }

    private void collectionSummary() {
        YearMonth month = Validator.month(ask("Month (yyyy-MM)"));
        ReportService.CollectionSummary s = reportService.collectionSummary(month, LocalDate.now());
        System.out.printf("Collection summary for %s%n  Bills generated : %d (%d fully paid)%n  Billed          : %s%n"
                        + "  Collected       : %s%n  Pending         : %s%n",
                s.month(), s.billsGenerated(), s.billsPaid(), Money.format(s.billed()),
                Money.format(s.collected()), Money.format(s.pending()));
    }

    private void receipt() {
        Path file = receiptService.generate(ask("Receipt number"), receiptsDir);
        System.out.println("Receipt saved to " + file.toAbsolutePath());
    }

    /** Creates sample flats, two months of bills and a mix of on-time, partial and late payments. */
    private void seedDemoData() {
        if (!flatService.listAll().isEmpty()) {
            System.out.println("Demo data is only loaded into an empty database.");
            return;
        }
        flatService.addFlat("A-101", "Ravi Kumar", "9876543210", "1000");
        flatService.addFlat("A-102", "Priya Nair", "9876543211", "1200");
        flatService.addFlat("B-201", "Arun Sharma", "9876543212", "1500");
        flatService.addFlat("B-202", "Meena Iyer", "9876543213", "1100");
        flatService.addFlat("C-301", "Karthik Raja", "9876543214", "1800");

        YearMonth older = YearMonth.now().minusMonths(2);
        YearMonth last = YearMonth.now().minusMonths(1);
        billingService.generateMonthlyBills(older);
        billingService.generateMonthlyBills(last);

        Bill a = bill("A-101", older);
        paymentService.recordPayment(a.id(), a.principal(), PaymentMode.UPI, a.dueDate().minusDays(2));
        Bill b = bill("A-102", older);
        paymentService.recordPayment(b.id(), new BigDecimal("1500"), PaymentMode.CASH, b.dueDate());
        Bill c = bill("B-201", older);
        LocalDate latePay = c.dueDate().plusDays(20);
        paymentService.recordPayment(c.id(), c.principal().add(calculator.lateFee(c.principal(), c.dueDate(), latePay)),
                PaymentMode.BANK_TRANSFER, latePay);
        Bill d = bill("A-101", last);
        paymentService.recordPayment(d.id(), d.principal(), PaymentMode.UPI, d.dueDate());
        System.out.println("Demo data loaded: 5 flats, bills for " + older + " and " + last + ", 4 payments.");
    }

    private Bill bill(String flatNumber, YearMonth month) {
        Flat flat = flatService.getByNumber(flatNumber);
        return billRepository.findByFlatAndMonth(flat.id(), month).orElseThrow();
    }

    private String ask(String prompt) {
        System.out.print(prompt + ": ");
        return in.hasNextLine() ? in.nextLine().trim() : "0";
    }
}
