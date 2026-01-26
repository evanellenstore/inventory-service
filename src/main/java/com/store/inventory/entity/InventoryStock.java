package com.store.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "inventory_stock",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {"product_id", "expiry_date"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "available_qty", nullable = false)
    private Integer availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private Integer reservedQty;

    @Column(name = "min_qty")
    private Integer minQty;

    @Column(name = "max_qty")
    private Integer maxQty;

    @Column(nullable = false, unique = true)
    private String batchNo;

    @Column(name = "purchase_price")
    private BigDecimal purchasePrice;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "manufacturing_date")
    private LocalDate manufacturingDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;   

    @Version
    private Integer version;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @PrePersist
    @PreUpdate
    private void onUpdate() {
        this.lastUpdated = LocalDateTime.now();
    }
}
