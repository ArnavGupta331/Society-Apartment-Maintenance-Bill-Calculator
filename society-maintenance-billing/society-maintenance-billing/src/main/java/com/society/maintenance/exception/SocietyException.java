package com.society.maintenance.exception;

/** Base type for every application-specific error. */
public class SocietyException extends RuntimeException {
    public SocietyException(String message) { super(message); }
    public SocietyException(String message, Throwable cause) { super(message, cause); }
}
