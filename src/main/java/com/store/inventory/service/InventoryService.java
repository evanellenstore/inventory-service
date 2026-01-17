package com.store.inventory.service;

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
}
