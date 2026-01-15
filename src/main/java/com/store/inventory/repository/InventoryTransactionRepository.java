package com.store.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.store.inventory.entity.InventoryTransaction;

public interface InventoryTransactionRepository
        extends JpaRepository<InventoryTransaction, Long> {
}
