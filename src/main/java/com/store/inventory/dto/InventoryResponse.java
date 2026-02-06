package com.store.inventory.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class InventoryResponse {
    public InventoryResponse(long productId2, String string, LocalDate mfgDate2, LocalDate expiryDate2, int maxQty2) {
        //TODO Auto-generated constructor stub
    }
    private Long productId;
    private Integer availableQty;
    private Integer reservedQty;
    private Integer minQty;
    private Integer maxQty;
     private LocalDate mfgDate;
    private LocalDate expiryDate;
}
