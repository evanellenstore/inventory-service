package com.store.inventory.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InventoryResponse {
    private Long productId;
    private Integer availableQty;
    private Integer reservedQty;
    private Integer minQty;
    private Integer maxQty;
}
