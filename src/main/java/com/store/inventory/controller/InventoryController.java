package com.store.inventory.controller;


import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.store.inventory.entity.Inventory;
import com.store.inventory.service.InventoryService;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @PostMapping
    public Inventory create(@RequestBody Inventory inventory) {
        return service.save(inventory);
    }

    @GetMapping("/{productCode}")
    public Inventory get(@PathVariable String productCode) {
        return service.getByProductCode(productCode);
    }

    @GetMapping
    public List<Inventory> getAll() {
        return service.getAll();
    }

    @PutMapping("/{productCode}/{qty}")
    public Inventory update(@PathVariable String productCode,
                            @PathVariable Integer qty) {
        return service.updateStock(productCode, qty);
    }
}

