package com.store.inventory.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryResponse {
    private Long productId;
    private String batchNo;
    private Integer availableQty;
    private Integer reservedQty;
    private Integer minQty;
    private Integer maxQty;
    private LocalDate manufacturingDate;
    private LocalDate expiryDate;
}
