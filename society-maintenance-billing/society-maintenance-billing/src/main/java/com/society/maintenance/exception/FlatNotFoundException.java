package com.society.maintenance.exception;

public class FlatNotFoundException extends SocietyException {
    public FlatNotFoundException(String flatNumber) { super("No flat found with number " + flatNumber); }
}
