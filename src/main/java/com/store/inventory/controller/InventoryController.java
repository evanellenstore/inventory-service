package com.store.inventory.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.store.inventory.dto.AdjustRequest;
import com.store.inventory.dto.InventorySummaryResponse;
import com.store.inventory.dto.ReserveRequest;
import com.store.inventory.dto.ReservedItemResponse;
import com.store.inventory.dto.VoiceCommand;
import com.store.inventory.entity.InventoryStock;
import com.store.inventory.service.InventoryService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping()
    public List<InventorySummaryResponse> getAll(
            @RequestParam(required = false) String stockStatus,
            @RequestParam(required = false) String expiryStatus) {
        
        // If any filter is provided, use the filtered endpoint
        if ((stockStatus != null && !stockStatus.isEmpty()) || 
            (expiryStatus != null && !expiryStatus.isEmpty())) {
            return inventoryService.getInventoryFiltered(stockStatus, expiryStatus);
        }
        
        // Otherwise return all
        return inventoryService.getAllInventory();
    }

    //================for voice integration===========================
    @PostMapping("/search")
    public List<InventorySummaryResponse> searchByName(@RequestBody Map<String, String> request) {
        String language = request.get("language");
        String textJson = request.get("text");
        ObjectMapper mapper = new ObjectMapper();
        VoiceCommand command = mapper.readValue(textJson, VoiceCommand.class);

         List<InventorySummaryResponse> resultList=inventoryService.searchByProductName(command.getProductName(),language);

        return resultList;
    }


    @GetMapping("/{productId}")
    public InventorySummaryResponse get(@PathVariable Long productId) {
        return inventoryService.getInventory(productId);
    }

    @GetMapping("/{productId}/reserved-items")
    public List<ReservedItemResponse> getReservedItems(@PathVariable Long productId) {
        return inventoryService.getReservedItems(productId);
    }


    @GetMapping("/reserved-items-all")
    public List<ReservedItemResponse> getReservedItemsList() {
        return inventoryService.getReservedItemAll();
    }

    @PutMapping("/{productId}/reserve")
    public void reserve(@PathVariable Long productId,@RequestParam String batchNo,
                        @RequestBody ReserveRequest req) {
        inventoryService.reserveStock(productId, batchNo, req);
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

    @GetMapping("/report")
    public List<?> getInventoryReports() {
        return inventoryService.getInventoryReports();
    }
}
