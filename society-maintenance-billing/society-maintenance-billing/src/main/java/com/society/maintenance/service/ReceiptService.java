package com.society.maintenance.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.society.maintenance.exception.BillNotFoundException;
import com.society.maintenance.exception.FlatNotFoundException;
import com.society.maintenance.exception.ReceiptGenerationException;
import com.society.maintenance.model.Bill;
import com.society.maintenance.model.Flat;
import com.society.maintenance.model.Payment;
import com.society.maintenance.repository.BillRepository;
import com.society.maintenance.repository.FlatRepository;
import com.society.maintenance.repository.PaymentRepository;
import com.society.maintenance.util.Money;
import com.society.maintenance.util.Validator;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates a one-page PDF receipt for a payment using OpenPDF. */
public class ReceiptService {
    private final FlatRepository flats;
    private final BillRepository bills;
    private final PaymentRepository payments;
    private final BillingCalculator calculator;

    public ReceiptService(FlatRepository flats, BillRepository bills, PaymentRepository payments, BillingCalculator calculator) {
        this.flats = flats;
        this.bills = bills;
        this.payments = payments;
        this.calculator = calculator;
    }

    public Path generate(String receiptNo, Path outputDir) {
        String no = Validator.requireNonBlank(receiptNo, "Receipt number");
        Payment payment = payments.findByReceiptNo(no)
                .orElseThrow(() -> new ReceiptGenerationException("No payment found with receipt number " + no));
        Bill bill = bills.findById(payment.billId()).orElseThrow(() -> new BillNotFoundException(payment.billId()));
        Flat flat = flats.findById(bill.flatId()).orElseThrow(() -> new FlatNotFoundException("id " + bill.flatId()));

        BigDecimal paidToDate = payments.totalPaid(bill.id());
        BigDecimal lateFee = calculator.effectiveLateFee(bill, payment.paidOn());
        BigDecimal balance = bill.principal().add(lateFee).subtract(paidToDate);

        Path file = outputDir.resolve(no + ".pdf");
        Document doc = new Document(PageSize.A5, 36, 36, 36, 36);
        try {
            Files.createDirectories(outputDir);
            PdfWriter.getInstance(doc, new FileOutputStream(file.toFile()));
            doc.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
            Font sub = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

            Paragraph name = new Paragraph(calculator.config().societyName(), title);
            name.setAlignment(Element.ALIGN_CENTER);
            doc.add(name);
            Paragraph heading = new Paragraph("MAINTENANCE PAYMENT RECEIPT", sub);
            heading.setAlignment(Element.ALIGN_CENTER);
            heading.setSpacingAfter(14);
            doc.add(heading);

            PdfPTable info = table(2);
            row(info, "Receipt No", payment.receiptNo(), bold, sub);
            row(info, "Payment Date", payment.paidOn().toString(), bold, sub);
            row(info, "Flat", flat.flatNumber(), bold, sub);
            row(info, "Owner", flat.ownerName(), bold, sub);
            row(info, "Bill Month", bill.billMonth().toString(), bold, sub);
            row(info, "Payment Mode", payment.mode().name().replace('_', ' '), bold, sub);
            info.setSpacingAfter(14);
            doc.add(info);

            PdfPTable breakdown = table(2);
            row(breakdown, "Maintenance", Money.format(bill.maintenance()), sub, sub);
            row(breakdown, "Water charges", Money.format(bill.water()), sub, sub);
            row(breakdown, "Sinking fund", Money.format(bill.sinkingFund()), sub, sub);
            row(breakdown, "Late fee", Money.format(lateFee), sub, sub);
            row(breakdown, "Total billed", Money.format(bill.principal().add(lateFee)), bold, bold);
            row(breakdown, "Paid in this receipt", Money.format(payment.amount()), bold, bold);
            row(breakdown, "Total paid to date", Money.format(paidToDate), sub, sub);
            row(breakdown, "Balance due", Money.format(balance), bold, bold);
            breakdown.setSpacingAfter(18);
            doc.add(breakdown);

            Paragraph foot = new Paragraph("This is a computer-generated receipt and does not need a signature.",
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8));
            foot.setAlignment(Element.ALIGN_CENTER);
            doc.add(foot);
        } catch (IOException | RuntimeException e) {
            throw new ReceiptGenerationException("Could not create receipt " + no, e);
        } catch (Exception e) { // OpenPDF's DocumentException
            throw new ReceiptGenerationException("Could not create receipt " + no, e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return file;
    }

    private static PdfPTable table(int columns) {
        PdfPTable t = new PdfPTable(columns);
        t.setWidthPercentage(100);
        return t;
    }

    private static void row(PdfPTable t, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell l = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell v = new PdfPCell(new Phrase(value, valueFont));
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (PdfPCell cell : new PdfPCell[]{l, v}) {
            cell.setPadding(5);
            cell.setBorderWidth(0.5f);
            cell.setBorderColor(java.awt.Color.LIGHT_GRAY);
        }
        t.addCell(l);
        t.addCell(v);
    }
}
