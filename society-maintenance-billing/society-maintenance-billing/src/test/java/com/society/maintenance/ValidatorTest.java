package com.society.maintenance;

import com.society.maintenance.exception.InvalidInputException;
import com.society.maintenance.util.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatorTest {

    @Test
    void flatNumberIsTrimmedAndUpperCased() {
        assertEquals("A-101", Validator.flatNumber("  a-101 "));
    }

    @Test
    void invalidFlatNumbersAreRejected() {
        assertThrows(InvalidInputException.class, () -> Validator.flatNumber(""));
        assertThrows(InvalidInputException.class, () -> Validator.flatNumber("A 101"));
        assertThrows(InvalidInputException.class, () -> Validator.flatNumber("TOO-LONG-FLAT-NUMBER"));
    }

    @Test
    void phoneMustBeTenDigitIndianMobile() {
        assertEquals("9876543210", Validator.phone("9876543210"));
        assertThrows(InvalidInputException.class, () -> Validator.phone("12345"));
        assertThrows(InvalidInputException.class, () -> Validator.phone("1234567890"));
        assertThrows(InvalidInputException.class, () -> Validator.phone("98765abcde"));
    }

    @Test
    void amountsMustBePositiveWithTwoDecimalsAtMost() {
        assertEquals(new BigDecimal("1500.50"), Validator.positiveDecimal("1500.5", "Amount"));
        assertThrows(InvalidInputException.class, () -> Validator.positiveDecimal("0", "Amount"));
        assertThrows(InvalidInputException.class, () -> Validator.positiveDecimal("-10", "Amount"));
        assertThrows(InvalidInputException.class, () -> Validator.positiveDecimal("12.345", "Amount"));
        assertThrows(InvalidInputException.class, () -> Validator.positiveDecimal("abc", "Amount"));
    }

    @Test
    void monthAndDateFormatsAreChecked() {
        assertEquals(YearMonth.of(2025, 3), Validator.month("2025-03"));
        assertThrows(InvalidInputException.class, () -> Validator.month("03/2025"));
        assertThrows(InvalidInputException.class, () -> Validator.date("15-01-2025"));
    }

    @Test
    void idsMustBePositiveWholeNumbers() {
        assertEquals(7L, Validator.id("7", "Bill id"));
        assertThrows(InvalidInputException.class, () -> Validator.id("0", "Bill id"));
        assertThrows(InvalidInputException.class, () -> Validator.id("x", "Bill id"));
    }
}
