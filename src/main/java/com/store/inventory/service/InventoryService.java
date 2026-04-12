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
import com.store.inventory.dto.InventoryReportDTO;
import com.store.inventory.dto.ProductResponse;
import com.store.inventory.dto.ReserveRequest;
import com.store.inventory.dto.ReservedItemResponse;
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
    public void reserveStock(Long productId,String batchNo, ReserveRequest req) {
        InventoryStock stock = stockRepo.findByProductIdAndBatchNo(productId, batchNo)
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
                .billId(req.getReferenceId())
                .build());
    }

    // -------------------------------
    // RELEASE STOCK
    // -------------------------------
    @Transactional
    public void releaseStock(Long productId, ReserveRequest req) {
        // Find the existing RESERVE transaction
        InventoryTransaction reserveTx = txRepo.findByProductIdAndReferenceIdAndType(
                productId, req.getReferenceId(), TransactionType.RESERVE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new InventoryException("Reserve transaction not found"));

        // Find the stock by checking which batch has reserved quantity
        List<InventoryStock> stocks = stockRepo.getByProductId(productId);
        
        InventoryStock stock = stocks.stream()
                .filter(s -> s.getReservedQty() >= req.getQuantity())
                .findFirst()
                .orElseThrow(() -> new InventoryException("No stock with sufficient reserved quantity"));

        if (stock.getReservedQty() < req.getQuantity()) {
            throw new InventoryException("Invalid release quantity");
        }

        stock.setReservedQty(stock.getReservedQty() - req.getQuantity());
        stock.setAvailableQty(stock.getAvailableQty() + req.getQuantity());

        stockRepo.save(stock);

        // Check if this is a full or partial release
        int remainingReservedQty = reserveTx.getQuantity() - req.getQuantity();
        
        if (remainingReservedQty == 0) {
            // Full release: Update the transaction type from RESERVE to IN
            reserveTx.setType(TransactionType.IN);
            txRepo.save(reserveTx);
        } else {
            // Partial release: Reduce the RESERVE transaction quantity and create new IN transaction
            reserveTx.setQuantity(remainingReservedQty);
            txRepo.save(reserveTx);
            
            // Create new IN transaction for the released quantity (stock going back to available)
            txRepo.save(InventoryTransaction.builder()
                    .productId(productId)
                    .type(TransactionType.IN)
                    .quantity(req.getQuantity())
                    .referenceId(req.getReferenceId())
                    .billId(reserveTx.getBillId())
                    .build());
        }
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
            // OUT adjustment should consume from reserved quantities (items that were reserved during billing)
            // If reserved qty is available, use it. Otherwise, use available qty (for non-reserved items)
            if (stock.getReservedQty() >= req.getQuantity()) {
                // Item was reserved during billing - decrease reserved quantity
                stock.setReservedQty(stock.getReservedQty() - req.getQuantity());
            } else if (stock.getAvailableQty() >= req.getQuantity()) {
                // Item was NOT reserved - decrease available quantity (shouldn't happen in normal flow)
                stock.setAvailableQty(stock.getAvailableQty() - req.getQuantity());
            } else {
                // Insufficient stock in either reserved or available
                throw new InventoryException(
                    String.format("Insufficient stock for OUT adjustment. Required: %d, Reserved: %d, Available: %d",
                        req.getQuantity(), stock.getReservedQty(), stock.getAvailableQty())
                );
            }
        }

        stockRepo.save(stock);

        // 🔑 KEY FIX: When OUT is requested, DELETE the RESERVE transaction and create OUT instead
        if (type == TransactionType.OUT) {
            // Look for ANY RESERVE transactions for this product (regardless of referenceId)
            // Most RESERVE from billing have NULL referenceId initially
            List<InventoryTransaction> reserveTxs = txRepo.findByProductIdAndType(productId, TransactionType.RESERVE);
            
            if (!reserveTxs.isEmpty()) {
                // ✅ DELETE all RESERVE transactions for this product
                for (InventoryTransaction reserveTx : reserveTxs) {
                    txRepo.delete(reserveTx);
                }
                // Now create single OUT transaction to replace all RESERVE entries
                txRepo.save(InventoryTransaction.builder()
                        .productId(productId)
                        .type(TransactionType.OUT)
                        .quantity(req.getQuantity())
                        .referenceId(req.getReferenceId())
                        .billId(req.getReferenceId())
                        .remarks(req.getRemarks())
                        .build());
            } else {
                // No RESERVE found, create new OUT transaction (for non-reserved items)
                txRepo.save(InventoryTransaction.builder()
                        .productId(productId)
                        .type(type)
                        .quantity(req.getQuantity())
                        .referenceId(req.getReferenceId())
                        .billId(req.getReferenceId())
                        .remarks(req.getRemarks())
                        .build());
            }
        } else {
            // For IN and other types, just create new transaction
            txRepo.save(InventoryTransaction.builder()
                    .productId(productId)
                    .type(type)
                    .quantity(req.getQuantity())
                    .referenceId(req.getReferenceId())
                    .billId(req.getReferenceId())
                    .remarks(req.getRemarks())
                    .build());
        }
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
        // ✅ If no stocks exist, return empty response with 0 quantities instead of throwing error
        if (stocks.isEmpty()) {
            try {
                ProductResponse product = productClient.getById(productId);
                return InventorySummaryResponse.builder()
                        .productId(String.valueOf(product.getId()))
                        .productSku(product.getSku())
                        .productName(product.getName())
                        .totalQty(0)
                        .batches(List.of())  // Empty batches list
                        .build();
            } catch (Exception e) {
                // If product not found, throw appropriate exception
                throw new InventoryException("Product not found: " + productId);
            }
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


    public List<InventoryStock> getBatchesByProductId(Long productId) {
        List<InventoryStock> batches = stockRepo.findByProductIdOrderByExpiryDateAsc(productId);
        
        // Filter to only include batches with available quantity
        return batches.stream()
                .filter(b -> b.getAvailableQty() != null && b.getAvailableQty() > 0)
                .sorted(Comparator.comparing(InventoryStock::getExpiryDate))
                .collect(Collectors.toList());
    }

    // -------------------------------
    // GET RESERVED ITEMS
    // -------------------------------
    public List<ReservedItemResponse> getReservedItems(Long productId) {
        // Get all RESERVE transactions for this product
        List<InventoryTransaction> reserveTransactions = txRepo.findByProductIdAndType(productId, TransactionType.RESERVE);
        
        // Get product details
        final ProductResponse[] productArray = new ProductResponse[1];
        try {
            productArray[0] = productClient.getById(productId);
        } catch (Exception e) {
            // Product service might not be available, continue without it
        }
        
        final ProductResponse product = productArray[0];
        
        // Map transactions to reserved items
        return reserveTransactions.stream()
                .map(tx -> ReservedItemResponse.builder()
                        .referenceId(tx.getReferenceId())
                        .quantity(tx.getQuantity())
                        .reservedDate(tx.getCreatedAt())
                        .sku(product != null ? product.getSku() : "N/A")
                        .productName(product != null ? product.getName() : "Unknown Product")
                        .productId(productId)
                        .build())
                .collect(Collectors.toList());
    }

    public List<ReservedItemResponse> getReservedItemAll() {
        List<InventoryTransaction> reserveTransactions = txRepo.findByType(TransactionType.RESERVE);
        Map<Long, List<InventoryTransaction>> grouped = reserveTransactions.stream()
                .collect(Collectors.groupingBy(InventoryTransaction::getProductId));

        List<ReservedItemResponse> result = new ArrayList<>();

        grouped.forEach((productId, transactions) -> {
            ProductResponse product = productClient.getById(productId);
            List<ReservedItemResponse> items = transactions.stream()
                    .map(tx -> ReservedItemResponse.builder()
                            .referenceId(tx.getReferenceId())
                            .quantity(tx.getQuantity())
                            .reservedDate(tx.getCreatedAt())
                            .sku(product != null ? product.getSku() : "N/A")
                            .productName(product != null ? product.getName() : "Unknown Product")
                            .productId(productId)
                            .build())
                    .collect(Collectors.toList());

            result.addAll(items);
        });

        return result;
    }

    /**
     * Get aggregated inventory report data (for reporting service)
     * Groups inventory by productId and calculates total available and reserved quantities
     */
    public List<InventoryReportDTO> getInventoryReports() {
        List<InventoryStock> stocks = stockRepo.findAll();

        // Group stocks by productId and sum quantities
        Map<Long, List<InventoryStock>> groupedByProduct = stocks.stream()
                .collect(Collectors.groupingBy(InventoryStock::getProductId));

        return groupedByProduct.entrySet().stream()
                .map(entry -> InventoryReportDTO.builder()
                        .productId(entry.getKey())
                        .availableQty((int) entry.getValue().stream()
                                .mapToLong(InventoryStock::getAvailableQty)
                                .sum())
                        .reservedQty((int) entry.getValue().stream()
                                .mapToLong(InventoryStock::getReservedQty)
                                .sum())
                        .build())
                .collect(Collectors.toList());
    }

}
