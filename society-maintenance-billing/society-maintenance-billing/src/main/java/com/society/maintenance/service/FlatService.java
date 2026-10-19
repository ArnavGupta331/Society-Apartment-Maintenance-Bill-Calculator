package com.society.maintenance.service;

import com.society.maintenance.exception.DuplicateFlatException;
import com.society.maintenance.exception.FlatNotFoundException;
import com.society.maintenance.model.Flat;
import com.society.maintenance.repository.FlatRepository;
import com.society.maintenance.util.Validator;

import java.math.BigDecimal;
import java.util.List;

public class FlatService {
    private final FlatRepository flats;

    public FlatService(FlatRepository flats) {
        this.flats = flats;
    }

    public Flat addFlat(String flatNumber, String ownerName, String phone, String areaSqFt) {
        String number = Validator.flatNumber(flatNumber);
        String owner = Validator.personName(ownerName);
        String mobile = Validator.phone(phone);
        BigDecimal area = Validator.positiveDecimal(areaSqFt, "Area");
        if (flats.findByNumber(number).isPresent()) {
            throw new DuplicateFlatException(number);
        }
        return flats.save(new Flat(0, number, owner, mobile, area));
    }

    public Flat getByNumber(String flatNumber) {
        String number = Validator.flatNumber(flatNumber);
        return flats.findByNumber(number).orElseThrow(() -> new FlatNotFoundException(number));
    }

    public List<Flat> listAll() {
        return flats.findAll();
    }
}
