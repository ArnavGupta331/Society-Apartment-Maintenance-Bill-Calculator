package com.society.maintenance.exception;

/** Wraps SQLException so upper layers never depend on JDBC types. */
public class DataAccessException extends SocietyException {
    public DataAccessException(String message, Throwable cause) { super(message, cause); }
}
