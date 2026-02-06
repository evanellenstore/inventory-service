package com.store.inventory.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.store.inventory.dto.AdjustRequest;
import com.store.inventory.dto.InventoryResponse;
import com.store.inventory.dto.ReserveRequest;
import com.store.inventory.entity.InventoryStock;
import com.store.inventory.entity.InventoryTransaction;
import com.store.inventory.entity.TransactionType;
import com.store.inventory.exception.InventoryException;
import com.store.inventory.repository.InventoryStockRepository;
import com.store.inventory.repository.InventoryTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor

public class InventoryService {

    private final InventoryStockRepository stockRepo;
    private final InventoryTransactionRepository txRepo;

    // -------------------------------
    // GET INVENTORY
    // -------------------------------
    public InventoryResponse getInventory(Long productId) {
        InventoryStock stock = stockRepo.findByProductId(productId)
                .orElseThrow(() -> new InventoryException("Stock not found"));

        return InventoryResponse.builder()
                .productId(productId)
                .availableQty(stock.getAvailableQty())
                .reservedQty(stock.getReservedQty())
                .minQty(stock.getMinQty())
                .maxQty(stock.getMaxQty())
                .build();
    }


    public List<InventoryResponse> getAllInventory() {

    List<InventoryStock> stocks = stockRepo.findAll();

    return stocks.stream()
            .map(stock -> InventoryResponse.builder()
                    .productId(stock.getProductId())
                    .availableQty(stock.getAvailableQty())
                    .reservedQty(stock.getReservedQty())
                    .minQty(stock.getMinQty())
                    .maxQty(stock.getMaxQty())
                    .build())
            .collect(Collectors.toList());
}

    

    // -------------------------------
    // RESERVE STOCK
    // -------------------------------
    @Transactional
    public void reserveStock(Long productId, ReserveRequest req) {
        InventoryStock stock = stockRepo.findByProductId(productId)
                .orElseThrow(() -> new InventoryException("Stock not found"));

        if (stock.getAvailableQty() < req.getQuantity()) {
            throw new InventoryException("Insufficient stock");
        }

        stock.setAvailableQty(stock.getAvailableQty() - req.getQuantity());
        stock.setReservedQty(stock.getReservedQty() + req.getQuantity());

        stockRepo.save(stock);

        txRepo.save(InventoryTransaction.builder()
                .productId(productId)
                .type(TransactionType.RESERVE)
                .quantity(req.getQuantity())
                .referenceId(req.getReferenceId())
                .build());
    }

    // -------------------------------
    // RELEASE STOCK
    // -------------------------------
    @Transactional
    public void releaseStock(Long productId, ReserveRequest req) {
        InventoryStock stock = stockRepo.findByProductId(productId)
                .orElseThrow(() -> new InventoryException("Stock not found"));

        if (stock.getReservedQty() < req.getQuantity()) {
            throw new InventoryException("Invalid release quantity");
        }

        stock.setReservedQty(stock.getReservedQty() - req.getQuantity());
        stock.setAvailableQty(stock.getAvailableQty() + req.getQuantity());

        stockRepo.save(stock);

        txRepo.save(InventoryTransaction.builder()
                .productId(productId)
                .type(TransactionType.RELEASE)
                .quantity(req.getQuantity())
                .referenceId(req.getReferenceId())
                .build());
    }

    // -------------------------------
    // ADJUST STOCK (CREATE IF NOT EXISTS)
    // -------------------------------
    @Transactional
    public void adjustStock(Long productId, AdjustRequest req) {
        InventoryStock stock = stockRepo.findByProductId(productId)
                .orElseGet(() -> {
                    InventoryStock s = new InventoryStock();
                    s.setProductId(productId);
                    s.setAvailableQty(0);
                    s.setReservedQty(0);
                    return s;
                });

        TransactionType type = TransactionType.valueOf(req.getType());

        if (type == TransactionType.IN) {
            stock.setAvailableQty(stock.getAvailableQty() + req.getQuantity());
        } else if (type == TransactionType.OUT) {
            if (stock.getAvailableQty() < req.getQuantity()) {
                throw new InventoryException("Insufficient stock for OUT adjustment");
            }
            stock.setAvailableQty(stock.getAvailableQty() - req.getQuantity());
        }

        stockRepo.save(stock);

        txRepo.save(InventoryTransaction.builder()
                .productId(productId)
                .type(type)
                .quantity(req.getQuantity())
                .remarks(req.getRemarks())
                .build());
    }

public List<InventoryResponse> getAllInventoryTransactions() {
        // For demo, creating sample data with MFG and Expiry dates
        List<InventoryResponse> inventoryList = new ArrayList<>();

        LocalDate mfgDate = LocalDate.now().minusDays(5); // 5 days ago
        LocalDate expiryDate = mfgDate.plusMonths(6);   // 6 months expiry

        inventoryList.add(InventoryResponse.builder()
                .productId(101L)
                .availableQty(500)
                .reservedQty(50)
                .minQty(20)
                .maxQty(1000)
                .mfgDate(mfgDate)
                .expiryDate(expiryDate)
                .build());

        return inventoryList;
}
public List<InventoryResponse> getInventoryByProductId(Long productId) {
        // For demo, creating sample data with MFG and Expiry dates
        List<InventoryResponse> inventoryList = new ArrayList<>();

        LocalDate mfgDate = LocalDate.now().minusDays(10); // 10 days ago
        LocalDate expiryDate = mfgDate.plusMonths(12);    // 12 months expiry

        inventoryList.add(InventoryResponse.builder()
                .productId(productId)
                .availableQty(300)
                .reservedQty(30)
                .minQty(10)
                .maxQty(500)
                .mfgDate(mfgDate)
                .expiryDate(expiryDate)
                .build());

        return inventoryList;
    
}
}
