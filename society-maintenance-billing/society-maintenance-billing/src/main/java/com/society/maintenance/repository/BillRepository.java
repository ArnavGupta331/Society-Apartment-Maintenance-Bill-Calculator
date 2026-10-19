package com.society.maintenance.repository;

import com.society.maintenance.exception.DataAccessException;
import com.society.maintenance.model.Bill;
import com.society.maintenance.model.BillStatus;
import com.society.maintenance.util.Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BillRepository {
    private final Database db;

    public BillRepository(Database db) {
        this.db = db;
    }

    public Bill save(Bill bill) {
        String sql = """
            INSERT INTO bills (flat_id, bill_month, maintenance, water, sinking_fund, late_fee, due_date, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, bill.flatId());
            ps.setString(2, bill.billMonth().toString());
            ps.setString(3, bill.maintenance().toPlainString());
            ps.setString(4, bill.water().toPlainString());
            ps.setString(5, bill.sinkingFund().toPlainString());
            ps.setString(6, bill.lateFee().toPlainString());
            ps.setString(7, bill.dueDate().toString());
            ps.setString(8, bill.status().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return bill.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save bill for flat id " + bill.flatId(), e);
        }
    }

    /** Used inside a transaction together with PaymentRepository.save. */
    public void update(Connection c, Bill bill) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE bills SET late_fee = ?, status = ? WHERE id = ?")) {
            ps.setString(1, bill.lateFee().toPlainString());
            ps.setString(2, bill.status().name());
            ps.setLong(3, bill.id());
            ps.executeUpdate();
        }
    }

    public Optional<Bill> findById(long id) {
        List<Bill> rows = query("SELECT * FROM bills WHERE id = ?", id);
        return rows.stream().findFirst();
    }

    public Optional<Bill> findByFlatAndMonth(long flatId, YearMonth month) {
        return query("SELECT * FROM bills WHERE flat_id = ? AND bill_month = ?", flatId, month.toString())
                .stream().findFirst();
    }

    public List<Bill> findByFlatId(long flatId) {
        return query("SELECT * FROM bills WHERE flat_id = ? ORDER BY bill_month", flatId);
    }

    public List<Bill> findByMonth(YearMonth month) {
        return query("SELECT * FROM bills WHERE bill_month = ? ORDER BY flat_id", month.toString());
    }

    /** Bills that are PENDING or PARTIAL. */
    public List<Bill> findOutstanding() {
        return query("SELECT * FROM bills WHERE status <> ? ORDER BY bill_month, flat_id", BillStatus.PAID.name());
    }

    private List<Bill> query(String sql, Object... params) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<Bill> result = new ArrayList<>();
                while (rs.next()) result.add(map(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query bills", e);
        }
    }

    private static Bill map(ResultSet rs) throws SQLException {
        return new Bill(rs.getLong("id"), rs.getLong("flat_id"), YearMonth.parse(rs.getString("bill_month")),
                new BigDecimal(rs.getString("maintenance")), new BigDecimal(rs.getString("water")),
                new BigDecimal(rs.getString("sinking_fund")), new BigDecimal(rs.getString("late_fee")),
                LocalDate.parse(rs.getString("due_date")), BillStatus.valueOf(rs.getString("status")));
    }
}
