package com.store.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AdjustRequest {
    private Integer quantity;
    private String type;   
    private String remarks;
    private BigDecimal purchasePrice;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
}
