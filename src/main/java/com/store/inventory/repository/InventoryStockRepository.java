package com.store.inventory.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.store.inventory.entity.InventoryStock;

public interface InventoryStockRepository
        extends JpaRepository<InventoryStock, Long> {

    Optional<InventoryStock> findByProductId(Long productId);

    @Query("SELECT s FROM InventoryStock s WHERE s.productId = ?1")
    List<InventoryStock> getByProductId(Long productId);

    Optional<InventoryStock> findByProductIdAndExpiryDate(Long productId, LocalDate expiryDate);

    long countByProductIdAndExpiryDate(Long productId, LocalDate expiryDate);

    List<InventoryStock> findByProductIdOrderByExpiryDateAsc(Long productId);
    

    

  
}

