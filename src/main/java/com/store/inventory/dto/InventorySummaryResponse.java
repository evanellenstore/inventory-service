package com.store.inventory.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventorySummaryResponse {
    private String productId;
    private String productSku;
    private String productName;
    private Integer totalQty;
    private List<BatchSummary> batches;

    @Setter
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BatchSummary {
        private String batchNo;
        private String expiry;
        private Integer qty;
        private String supplierName;

      
    }

}
