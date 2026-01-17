package com.store.inventory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.store.inventory.dto.InventoryResponse;
import com.store.inventory.service.InventoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inventory/transactions")
@RequiredArgsConstructor
public class InventoryTransactionController {

    private final InventoryService inventoryService;

    @GetMapping()
    public List<InventoryResponse> getAllInventoryTransactions() {
        //return  inventoryService.getAllInventoryTransactions();
        return null;
    }

}
