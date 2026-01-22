package com.store.inventory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.store.inventory.dto.AdjustRequest;
import com.store.inventory.dto.InventorySummaryResponse;
import com.store.inventory.dto.ReserveRequest;
import com.store.inventory.entity.InventoryStock;
import com.store.inventory.service.InventoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping()
    public List<InventorySummaryResponse> getAll() {
        return  inventoryService.getAllInventory();
    }


    @GetMapping("/{productId}")
    public InventorySummaryResponse get(@PathVariable Long productId) {
        return inventoryService.getInventory(productId);
    }

    @PutMapping("/{productId}/reserve")
    public void reserve(@PathVariable Long productId,
                        @RequestBody ReserveRequest req) {
        inventoryService.reserveStock(productId, req);
    }

    @PutMapping("/{productId}/release")
    public void release(@PathVariable Long productId,
                        @RequestBody ReserveRequest req) {
        inventoryService.releaseStock(productId, req);
    }

    @PutMapping("/{productId}/adjust")
    public void adjust(@PathVariable Long productId,
                       @RequestBody AdjustRequest req) {
        inventoryService.adjustStock(productId, req);
    }

    @GetMapping("/batches")
    public List<InventoryStock> getBatches(
            @RequestParam Long productId) {

        return inventoryService.getBatchesByProductId(productId);
    }
}
