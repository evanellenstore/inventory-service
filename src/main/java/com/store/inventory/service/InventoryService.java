package com.store.inventory.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.store.inventory.entity.Inventory;
import com.store.inventory.repository.InventoryRepository;

@Service
public class InventoryService {

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    public Inventory save(Inventory inventory) {
        return repository.save(inventory);
    }

    public Inventory getByProductCode(String productCode) {
        return repository.findByProductCode(productCode)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public List<Inventory> getAll() {
        return repository.findAll();
    }

    public Inventory updateStock(String productCode, Integer qty) {
        Inventory inv = getByProductCode(productCode);
        inv.setQuantity(qty);
        return repository.save(inv);
    }
}
