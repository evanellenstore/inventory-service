package com.store.inventory.repository;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.store.inventory.entity.Inventory;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductCode(String productCode);
}

