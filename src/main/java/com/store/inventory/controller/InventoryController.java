package com.store.inventory.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
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
import com.store.inventory.entity.InventoryStock;
import com.store.inventory.service.InventoryService;
import com.store.inventory.utilty.HelperUtilities;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper; 

    // Pre-compiled Regex Patterns for performance
    private static final Pattern WEIGHT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|g|gm|gram|grams)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VOLUME_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(l|litre|liter|ml|millilitre|milliliter)", Pattern.CASE_INSENSITIVE);

    @GetMapping
    public List<InventorySummaryResponse> getAll(
            @RequestParam(required = false) String stockStatus,
            @RequestParam(required = false) String expiryStatus) {
        
        if ((stockStatus != null && !stockStatus.isEmpty()) || (expiryStatus != null && !expiryStatus.isEmpty())) {
            return inventoryService.getInventoryFiltered(stockStatus, expiryStatus);
        }
        return inventoryService.getAllInventory();
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
    public void reserve(@PathVariable Long productId, @RequestParam String batchNo, @RequestBody ReserveRequest req) {
        inventoryService.reserveStock(productId, batchNo, req);
    }

    @PutMapping("/{productId}/release")
    public void release(@PathVariable Long productId, @RequestBody ReserveRequest req) {
        inventoryService.releaseStock(productId, req);
    }

    @PutMapping("/{productId}/adjust")
    public void adjust(@PathVariable Long productId, @RequestBody AdjustRequest req) {
        inventoryService.adjustStock(productId, req);
    }

    @GetMapping("/batches")
    public List<InventoryStock> getBatches(@RequestParam Long productId) {
        return inventoryService.getBatchesByProductId(productId);
    }

    @GetMapping("/report")
    public List<?> getInventoryReports() {
        return inventoryService.getInventoryReports();
    }



@PostMapping("/search")
    public ResponseEntity<?> searchByName(@RequestBody Map<String, Object> request) {
        try {
            return processVoiceSearch(request);
        } catch (RuntimeException e) {
            return ResponseEntity.ok().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Dedicated strategy method handling multi-brand, packaging validation, and cart calculations.
     */
    private ResponseEntity<?> processVoiceSearch(Map<String, Object> request) throws RuntimeException {
        int requestedQty = request.get("qty") != null ? Integer.parseInt(request.get("qty").toString()) : 0;
        String requestedUnit = request.get("unit") != null ? request.get("unit").toString() : null;
        String language = request.get("language") != null ? request.get("language").toString() : "en";
        String productName = request.get("productName") != null ? request.get("productName").toString() : "";
        String explicitBrand = request.get("brand") != null ? request.get("brand").toString() : null;

        // Safely extract and check for literal null strings or true null references
        Boolean requestedIsLoose = null;
        if (request.get("isLoose") != null) {
            String isLooseStr = request.get("isLoose").toString().trim();
            if (!isLooseStr.equalsIgnoreCase("null") && !isLooseStr.isEmpty()) {
                requestedIsLoose = Boolean.parseBoolean(isLooseStr);
            }
        }

        // Fetch initial dataset by product name (Turn 1 baseline data)
        List<InventorySummaryResponse> resultList = inventoryService.searchByProductName(productName, language);
       
        // STEP 1: Handle Brand Filters (Second & Third Time Searches)
        if (explicitBrand != null && !explicitBrand.trim().isEmpty()) {
            String brandLc = explicitBrand.trim().toLowerCase();
            resultList = resultList.stream()
                .filter(r -> r.getBrandName() != null && r.getBrandName().toLowerCase().contains(brandLc))
                .collect(Collectors.toList());
        }
        
        if (resultList == null || resultList.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // STEP 2: Multi-brand Evaluation (Triggers on Turn 1 if multiple brands exist)
        List<String> rawBrands = resultList.stream()
            .map(InventorySummaryResponse::getBrandName)
            .filter(b -> b != null && !b.isEmpty())
            .distinct()
            .collect(Collectors.toList());     
            
        if (rawBrands.size() > 1 && (explicitBrand == null || explicitBrand.trim().isEmpty())) {
            return HelperUtilities.buildMultiBrandResponse(rawBrands, resultList, requestedQty, requestedUnit);
        }

        // STEP 3: Mixed Packaging Ambiguity Check (Triggers on Turn 2 if brand is selected but packaging isn't)
        if (requestedIsLoose == null) {
            boolean containsLoose = resultList.stream().anyMatch(r -> Boolean.TRUE.equals(r.isLoose()));
            boolean containsPacket = resultList.stream().anyMatch(r -> !Boolean.TRUE.equals(r.isLoose()));

            if (containsLoose && containsPacket) {
                Map<String, Object> body = new HashMap<>();
                body.put("multiBrand", false);
                body.put("needsPackagingClarification", true);
                body.put("prompt", "Do you want loose or packet?");
                body.put("candidates", resultList.stream().map(HelperUtilities::buildCandidateMap).collect(Collectors.toList()));
                return ResponseEntity.ok(body);
            }
        }

        // STEP 4: Run Structural Unit Matching & Quantity Metric Sifting
        List<InventorySummaryResponse> filtered = HelperUtilities.filterInventoryByUnit(resultList, requestedUnit, requestedQty, requestedIsLoose);

        // STEP 5: Routing Single Output vs Multi-Item Fallbacks / Mismatch Additions
        if (filtered.size() == 1) {
            InventorySummaryResponse r = filtered.get(0);
            Map<String, Object> candidateMap = HelperUtilities.buildCandidateMap(r);
            
            // This will execute safely on the isolated variant and throw standard mismatches if quantity checks fail
            int checkoutQty = HelperUtilities.calculateRequiredQty(r, requestedUnit, requestedQty, requestedIsLoose);

            Map<String, Object> body = new HashMap<>();
            body.put("requestedQty", requestedQty);
            body.put("requestedUnit", requestedUnit);
            body.put("multiBrand", false);
            body.put("checkoutQty", checkoutQty); 
            
            Map<String, Object> mutableCandidate = new HashMap<>(candidateMap);
            mutableCandidate.put("targetCartQty", checkoutQty);
            body.put("candidate", mutableCandidate);
            
            return ResponseEntity.ok(body);
        }

        // Only fall back to complete listings if the user HAS NOT chosen a packaging path yet.
        // If they chose "packet" (requestedIsLoose == false) and it's empty, do not fall back to loose.
        if (filtered.isEmpty() && !resultList.isEmpty() && requestedIsLoose == null) {
            return ResponseEntity.ok(Map.of(
                "multiBrand", false, 
                "candidates", resultList.stream().map(HelperUtilities::buildCandidateMap).collect(Collectors.toList())
            ));
        }

        return ResponseEntity.ok(filtered);
    }

}