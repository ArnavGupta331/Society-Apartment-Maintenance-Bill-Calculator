package com.society.maintenance.util;

import com.society.maintenance.exception.DataAccessException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Opens SQLite connections, creates the schema and offers a small transaction helper. */
public final class Database {

    @FunctionalInterface
    public interface TxWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final String url;

    public Database(String jdbcUrl) {
        this.url = jdbcUrl;
        initSchema();
    }

    public static Database forFile(Path file) {
        return new Database("jdbc:sqlite:" + file.toAbsolutePath());
    }

    public Connection connect() {
        try {
            Connection c = DriverManager.getConnection(url);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
            }
            return c;
        } catch (SQLException e) {
            throw new DataAccessException("Could not open the database", e);
        }
    }

    /** Runs the work in one transaction: commit on success, rollback on any failure. */
    public <T> T inTransaction(TxWork<T> work) {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database transaction failed", e);
        }
    }

    private void initSchema() {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS flats (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    flat_number TEXT NOT NULL UNIQUE,
                    owner_name  TEXT NOT NULL,
                    phone       TEXT NOT NULL,
                    area_sqft   TEXT NOT NULL
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS bills (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    flat_id     INTEGER NOT NULL REFERENCES flats(id),
                    bill_month  TEXT NOT NULL,
                    maintenance TEXT NOT NULL,
                    water       TEXT NOT NULL,
                    sinking_fund TEXT NOT NULL,
                    late_fee    TEXT NOT NULL,
                    due_date    TEXT NOT NULL,
                    status      TEXT NOT NULL,
                    UNIQUE (flat_id, bill_month)
                )""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS payments (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    bill_id    INTEGER NOT NULL REFERENCES bills(id),
                    amount     TEXT NOT NULL,
                    paid_on    TEXT NOT NULL,
                    mode       TEXT NOT NULL,
                    receipt_no TEXT NOT NULL UNIQUE
                )""");
            s.execute("CREATE INDEX IF NOT EXISTS idx_bills_status ON bills(status)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_payments_bill ON payments(bill_id)");
        } catch (SQLException e) {
            throw new DataAccessException("Could not create the database schema", e);
        }
    }
}
