package com.society.maintenance.repository;

import com.society.maintenance.exception.DataAccessException;
import com.society.maintenance.model.Flat;
import com.society.maintenance.util.Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FlatRepository {
    private final Database db;

    public FlatRepository(Database db) {
        this.db = db;
    }

    public Flat save(Flat flat) {
        String sql = "INSERT INTO flats (flat_number, owner_name, phone, area_sqft) VALUES (?, ?, ?, ?)";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, flat.flatNumber());
            ps.setString(2, flat.ownerName());
            ps.setString(3, flat.phone());
            ps.setString(4, flat.areaSqFt().toPlainString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Flat(keys.getLong(1), flat.flatNumber(), flat.ownerName(), flat.phone(), flat.areaSqFt());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save flat " + flat.flatNumber(), e);
        }
    }

    public Optional<Flat> findById(long id) {
        return findOne("SELECT * FROM flats WHERE id = ?", ps -> ps.setLong(1, id));
    }

    public Optional<Flat> findByNumber(String flatNumber) {
        return findOne("SELECT * FROM flats WHERE flat_number = ?", ps -> ps.setString(1, flatNumber));
    }

    public List<Flat> findAll() {
        List<Flat> result = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM flats ORDER BY flat_number");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(map(rs));
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list flats", e);
        }
    }

    @FunctionalInterface
    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private Optional<Flat> findOne(String sql, Binder binder) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to query flats", e);
        }
    }

    private static Flat map(ResultSet rs) throws SQLException {
        return new Flat(rs.getLong("id"), rs.getString("flat_number"), rs.getString("owner_name"),
                rs.getString("phone"), new BigDecimal(rs.getString("area_sqft")));
    }
}
