package com.society.maintenance.model;

import java.math.BigDecimal;

public record Flat(long id, String flatNumber, String ownerName, String phone, BigDecimal areaSqFt) { }
