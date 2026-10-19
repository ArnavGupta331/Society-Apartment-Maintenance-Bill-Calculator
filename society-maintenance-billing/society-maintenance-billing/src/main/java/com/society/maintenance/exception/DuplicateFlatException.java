package com.society.maintenance.exception;

public class DuplicateFlatException extends SocietyException {
    public DuplicateFlatException(String flatNumber) { super("Flat " + flatNumber + " is already registered"); }
}
