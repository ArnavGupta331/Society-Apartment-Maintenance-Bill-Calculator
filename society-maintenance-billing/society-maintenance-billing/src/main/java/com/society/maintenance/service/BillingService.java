package com.society.maintenance.service;

import com.society.maintenance.exception.DuplicateBillException;
import com.society.maintenance.exception.InvalidInputException;
import com.society.maintenance.model.Bill;
import com.society.maintenance.model.Flat;
import com.society.maintenance.repository.BillRepository;
import com.society.maintenance.repository.FlatRepository;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BillingService {
    private final FlatRepository flats;
    private final BillRepository bills;
    private final BillingCalculator calculator;

    public BillingService(FlatRepository flats, BillRepository bills, BillingCalculator calculator) {
        this.flats = flats;
        this.bills = bills;
        this.calculator = calculator;
    }

    /**
     * Generates one bill per flat for the month. Flats that already have a bill are skipped, so the
     * operation is safe to re-run after adding a new flat. Fails only if nothing new could be created.
     */
    public List<Bill> generateMonthlyBills(YearMonth month) {
        Objects.requireNonNull(month, "month");
        List<Flat> allFlats = flats.findAll();
        if (allFlats.isEmpty()) {
            throw new InvalidInputException("No flats are registered yet. Add flats before generating bills");
        }
        List<Bill> created = new ArrayList<>();
        for (Flat flat : allFlats) {
            if (bills.findByFlatAndMonth(flat.id(), month).isEmpty()) {
                created.add(bills.save(calculator.buildBill(flat, month)));
            }
        }
        if (created.isEmpty()) {
            throw new DuplicateBillException("Bills for " + month + " have already been generated for every flat");
        }
        return created;
    }

    public List<Bill> billsForFlat(Flat flat) {
        return bills.findByFlatId(flat.id());
    }
}
