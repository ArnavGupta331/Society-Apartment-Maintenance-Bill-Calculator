package com.society.maintenance.exception;

public class BillNotFoundException extends SocietyException {
    public BillNotFoundException(long billId) { super("No bill found with id " + billId); }
}
