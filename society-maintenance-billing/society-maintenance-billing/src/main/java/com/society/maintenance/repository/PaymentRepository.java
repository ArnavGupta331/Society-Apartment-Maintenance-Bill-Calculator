package com.society.maintenance.repository;

import com.society.maintenance.exception.DataAccessException;
import com.society.maintenance.model.Payment;
import com.society.maintenance.model.PaymentMode;
import com.society.maintenance.util.Database;
import com.society.maintenance.util.Money;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PaymentRepository {
    private final Database db;

    public PaymentRepository(Database db) {
        this.db = db;
    }

    /** Used inside a transaction together with BillRepository.update. */
    public Payment save(Connection c, Payment p) throws SQLException {
        String sql = "INSERT INTO payments (bill_id, amount, paid_on, mode, receipt_no) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, p.billId());
            ps.setString(2, p.amount().toPlainString());
            ps.setString(3, p.paidOn().toString());
            ps.setString(4, p.mode().name());
            ps.setString(5, p.receiptNo());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return p.withId(keys.getLong(1));
            }
        }
    }

    public List<Payment> findByBillId(long billId) {
        return query("SELECT * FROM payments WHERE bill_id = ? ORDER BY id", billId);
    }

    public Optional<Payment> findByReceiptNo(String receiptNo) {
        return query("SELECT * FROM payments WHERE receipt_no = ?", receiptNo).stream().findFirst();
    }

    public BigDecimal totalPaid(long billId) {
        BigDecimal total = Money.ZERO;
        for (Payment p : findByBillId(billId)) total = total.add(p.amount());
        return total;
    }

    private List<Payment> query(String sql, Object param) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<Payment> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new Payment(rs.getLong("id"), rs.getLong("bill_id"),
                            new BigDecimal(rs.getString("amount")), LocalDate.parse(rs.getString("paid_on")),
                            PaymentMode.valueOf(rs.getString("mode")), rs.getString("receipt_no")));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query payments", e);
        }
    }
}
