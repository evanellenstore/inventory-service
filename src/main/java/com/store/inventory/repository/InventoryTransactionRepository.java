package com.store.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.store.inventory.entity.InventoryTransaction;
import com.store.inventory.entity.TransactionType;
import java.util.List;

public interface InventoryTransactionRepository
        extends JpaRepository<InventoryTransaction, Long> {
    
    List<InventoryTransaction> findByProductIdAndReferenceIdAndType(Long productId, String referenceId, TransactionType type);
    
    List<InventoryTransaction> findByProductIdAndType(Long productId, TransactionType type);
    List<InventoryTransaction> findByType(TransactionType type);
}
