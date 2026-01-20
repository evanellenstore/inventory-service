package com.store.inventory.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.store.inventory.client.ProductServiceClient;
import com.store.inventory.dto.AdjustRequest;
import com.store.inventory.dto.InventoryResponse;
import com.store.inventory.dto.InventorySummaryResponse;
import com.store.inventory.dto.ProductResponse;
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
    private final ProductServiceClient productClient;

    // -------------------------------
    // GET INVENTORY
    // -------------------------------

    public InventorySummaryResponse getInventory(Long productId) {

        List<InventoryStock> stocks = stockRepo.getByProductId(productId);
        return toSummaryResponse(stocks, productId);
    }

    public List<InventorySummaryResponse> getAllInventory() {

        List<InventoryStock> stocks = stockRepo.findAll();

        // 1️⃣ Group stocks by productId
        Map<Long, List<InventoryStock>> groupedByProduct = stocks.stream()
                .collect(Collectors.groupingBy(InventoryStock::getProductId));

        List<InventorySummaryResponse> response = new ArrayList<>();

        // 2️⃣ Build response per product
        for (Map.Entry<Long, List<InventoryStock>> entry : groupedByProduct.entrySet()) {
            Long productId = entry.getKey();
            List<InventoryStock> productStocks = entry.getValue();

            response.add(toSummaryResponse(productStocks, productId));
        }

        return response;

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
        InventoryStock stock = stockRepo.findByProductIdAndExpiryDate(productId, req.getExpiryDate())
                .orElseGet(() -> {
                    InventoryStock s = new InventoryStock();
                    s.setProductId(productId);
                    s.setAvailableQty(0);
                    s.setReservedQty(0);
                    s.setManufacturingDate(req.getManufacturingDate());
                    s.setExpiryDate(req.getExpiryDate());
                    String batchNo = generateBatchNo(productId, req.getExpiryDate());
                    s.setBatchNo(batchNo);
                    s.setCreatedAt(LocalDateTime.now());
                    s.setSupplierName(req.getSupplierName());
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

    private String generateBatchNo(Long productId, LocalDate expiryDate) {
        return "P" + productId + "-" +
                expiryDate.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private InventoryResponse toResponse(InventoryStock inv) {
        return InventoryResponse.builder()
                .productId(inv.getProductId())
                .batchNo(inv.getBatchNo())
                .expiryDate(inv.getExpiryDate())
                .manufacturingDate(inv.getManufacturingDate())
                .availableQty(inv.getAvailableQty())
                .reservedQty(inv.getReservedQty())
                .supplierName(inv.getSupplierName())    
                .build();
    }

    private InventorySummaryResponse toSummaryResponse(List<InventoryStock> stocks, Long productId) {
        if (stocks.isEmpty()) {
            throw new RuntimeException("Product not found");
        }

        int totalQty = stocks.stream()
                .mapToInt(InventoryStock::getAvailableQty)
                .sum();

        List<InventorySummaryResponse.BatchSummary> batches = stocks.stream()
                .sorted(Comparator.comparing(InventoryStock::getExpiryDate))
                .map(s -> {
                    InventorySummaryResponse.BatchSummary b = new InventorySummaryResponse.BatchSummary();
                    b.setBatchNo(s.getBatchNo());
                    b.setExpiry(s.getExpiryDate().toString());
                    b.setQty(s.getAvailableQty());
                    b.setSupplierName(s.getSupplierName());
                    return b;
                })
                .toList();

        ProductResponse product = productClient.getById(productId);

        InventorySummaryResponse response = new InventorySummaryResponse();

        response.setProductId(String.valueOf(product.getId()));
        response.setProductSku(product.getSku());
        response.setProductName(product.getName());
        response.setTotalQty(totalQty);
        response.setBatches(batches);
        

        return response;
    }

}
