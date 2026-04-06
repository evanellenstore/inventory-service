package com.store.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservedItemResponse {
    private String referenceId;
    private Integer quantity;
    private LocalDateTime reservedDate;
    private String sku;
    private String productName;
    private Long productId;
}
