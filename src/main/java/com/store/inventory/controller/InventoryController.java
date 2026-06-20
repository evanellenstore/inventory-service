package com.store.inventory.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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



    //================ For Voice Integration ===========================
    @PostMapping("/search")
    public ResponseEntity<?> searchByName(@RequestBody Map<String, String> request) {
        String language = request.get("language");
        String textJson = request.get("text");
        
        VoiceCommand command;
        try {
            command = objectMapper.readValue(textJson, VoiceCommand.class);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid voice command", "details", e.getMessage()));
        }

        // Delegate execution to the optimized processing engine method
        return processVoiceSearch(command, language);
    }

    /**
     * Dedicated strategy method handling multi-brand and single-brand voice orchestration
     */
    private ResponseEntity<?> processVoiceSearch(VoiceCommand command, String language) {
        int requestedQty = command.getQty() != null ? command.getQty() : 0;
        String requestedUnit = command.getUnit();

        List<InventorySummaryResponse> resultList = inventoryService.searchByProductName(command.getProductName(), language);
       
        // Step 1: Explicit brand server-side filtering
        String explicitBrand = command.getBrand();
        if (explicitBrand != null && !explicitBrand.trim().isEmpty()) {
            String brandLc = explicitBrand.trim().toLowerCase();
            resultList = resultList.stream()
                .filter(r -> r.getBrandName() != null && r.getBrandName().toLowerCase().contains(brandLc))
                .collect(Collectors.toList());
        }
        
        if (resultList == null || resultList.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // Step 2: Multi-brand Evaluation
        List<String> rawBrands = resultList.stream()
            .map(InventorySummaryResponse::getBrandName)
            .filter(b -> b != null && !b.isEmpty())
            .distinct()
            .collect(Collectors.toList());
            
        if (rawBrands.size() > 1) {
            return buildMultiBrandResponse(rawBrands, resultList, requestedQty, requestedUnit);
        }

        // Step 3: Single-brand structural measurement units filter
        List<InventorySummaryResponse> filtered = resultList;
        if (requestedUnit != null && !requestedUnit.isEmpty() && requestedQty > 0) {
            final String voiceUnit = requestedUnit.trim().toLowerCase();
            final double requestedAmountInKg = toKg(requestedQty, voiceUnit);
            final double requestedAmountInL = toLitre(requestedQty, voiceUnit);

            filtered = resultList.stream()
                    .filter(r -> {
                        if (r.getTotalQty() == null) return false;
                        String productUnit = r.getUnit() != null ? r.getUnit().trim().toLowerCase() : "";

                        if (!productUnit.isEmpty() && productUnit.equalsIgnoreCase(voiceUnit)) {
                            return r.getTotalQty() >= requestedQty;
                        }

                        if (requestedAmountInKg > 0 && !isWeightUnit(productUnit)) {
                            Double packetKg = parseWeightKgFromText(r.getProductName());
                            if (packetKg == null) packetKg = parseWeightKgFromText(r.getProductSku());
                            if (packetKg == null) return false;
                            return r.getTotalQty() >= (int) Math.ceil(requestedAmountInKg / packetKg);
                        }

                        if (requestedAmountInL > 0 && !isVolumeUnit(productUnit)) {
                            Double packetL = parseVolumeLFromText(r.getProductName());
                            if (packetL == null) packetL = parseVolumeLFromText(r.getProductSku());
                            if (packetL == null) return false;
                            return r.getTotalQty() >= (int) Math.ceil(requestedAmountInL / packetL);
                        }

                        if (requestedAmountInKg <= 0 && isWeightUnit(productUnit)) {
                            Double packetKg = parseWeightKgFromText(r.getProductName());
                            if (packetKg == null) packetKg = parseWeightKgFromText(r.getProductSku());
                            if (packetKg == null) return false;
                            return productQtyToKg(r.getTotalQty(), productUnit) >= (requestedQty * packetKg);
                        }

                        if (requestedAmountInL <= 0 && isVolumeUnit(productUnit)) {
                            Double packetL = parseVolumeLFromText(r.getProductName());
                            if (packetL == null) packetL = parseVolumeLFromText(r.getProductSku());
                            if (packetL == null) return false;
                            return productQtyToL(r.getTotalQty(), productUnit) >= (requestedQty * packetL);
                        }

                        return false;
                    })
                    .collect(Collectors.toList());
        }

        // Step 4: Routing single output vs fallbacks
        if (filtered.size() == 1) {
            InventorySummaryResponse r = filtered.get(0);
            Map<String, Object> body = new HashMap<>();
            body.put("requestedQty", requestedQty);
            body.put("requestedUnit", requestedUnit);
            body.put("multiBrand", false);
            body.put("candidate", buildCandidateMap(r));
            body.put("availability", computeAvailability(r, requestedQty, requestedUnit));
            return ResponseEntity.ok(body);
        }

        if (filtered.isEmpty() && resultList.size() > 1) {
            List<String> brands = resultList.stream()
                    .map(InventorySummaryResponse::getBrandName)
                    .filter(b -> b != null && !b.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
            
            if (brands.size() <= 1) {
                return ResponseEntity.ok(Map.of("multiBrand", false, "candidates", 
                    resultList.stream().map(this::buildCandidateMap).collect(Collectors.toList())));
            }
            return buildMultiBrandResponse(brands, resultList, requestedQty, requestedUnit);
        }

        if (filtered.size() > 1) {
            List<String> brands = filtered.stream()
                    .map(InventorySummaryResponse::getBrandName)
                    .filter(b -> b != null && !b.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
            return buildMultiBrandResponse(brands, filtered, requestedQty, requestedUnit);
        }

        return ResponseEntity.ok(filtered);
    }

    /* ================= Helper Utilities ================= */

    private Map<String, Object> buildCandidateMap(InventorySummaryResponse r) {
        return Map.of(
            "productId", r.getProductId(),
            "productSku", r.getProductSku(),
            "productName", r.getProductName(),
            "brand", r.getBrandName() != null ? r.getBrandName() : "",
            "unit", r.getUnit() != null ? r.getUnit() : "",
            "totalQty", r.getTotalQty() != null ? r.getTotalQty() : 0,
            "price", r.getPrice() != null ? r.getPrice() : 0.0,
            "discountAmount", r.getDiscountAmount() != null ? r.getDiscountAmount() : 0.0
        );
    }

    private ResponseEntity<Map<String, Object>> buildMultiBrandResponse(List<String> brands, List<InventorySummaryResponse> candidates, int qty, String unit) {
        Map<String, Object> body = new HashMap<>();
        body.put("prompt", buildBrandPrompt(brands));
        body.put("options", brands);
        body.put("multiBrand", true);
        body.put("quantityIgnored", true);
        body.put("requestedQty", qty);
        body.put("requestedUnit", unit);
        body.put("candidates", candidates.stream().map(this::buildCandidateMap).collect(Collectors.toList()));
        return ResponseEntity.ok(body);
    }

    private String buildBrandPrompt(List<String> brands) {
        if (brands == null || brands.isEmpty()) {
            return "Multiple products found. Which brand do you want?";
        }
        StringBuilder sb = new StringBuilder("Multiple brands found: ");
        for (int i = 0; i < brands.size(); i++) {
            sb.append(i + 1).append(". ").append(brands.get(i));
            if (i < brands.size() - 1) sb.append(", ");
        }
        sb.append(". Which brand do you want?");
        return sb.toString();
    }

    private double toKg(int qty, String unit) {
        if (unit == null) return -1;
        unit = unit.trim().toLowerCase();
        if (unit.equals("kg") || unit.equals("kilogram") || unit.equals("kilograms")) return qty;
        if (unit.equals("g") || unit.equals("gram") || unit.equals("grams") || unit.equals("gm")) return qty / 1000.0;
        return -1;
    }

    private boolean isWeightUnit(String unit) {
        if (unit == null) return false;
        unit = unit.trim().toLowerCase();
        return unit.equals("kg") || unit.equals("kilogram") || unit.equals("kilograms") || unit.equals("g") || unit.equals("gram") || unit.equals("grams") || unit.equals("gm");
    }

    private Double parseWeightKgFromText(String text) {
        if (text == null) return null;
        Matcher m = WEIGHT_PATTERN.matcher(text);
        if (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1));
                String u = m.group(2).toLowerCase();
                return (u.equals("g") || u.equals("gm") || u.equals("gram") || u.equals("grams")) ? val / 1000.0 : val;
            } catch (Exception e) { return null; }
        }
        return null;
    }

    private Double parseVolumeLFromText(String text) {
        if (text == null) return null;
        Matcher m = VOLUME_PATTERN.matcher(text);
        if (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1));
                String u = m.group(2).toLowerCase();
                return (u.equals("ml") || u.equals("millilitre") || u.equals("milliliter")) ? val / 1000.0 : val;
            } catch (Exception e) { return null; }
        }
        return null;
    }

    private double productQtyToKg(Integer qty, String productUnit) {
        if (qty == null || productUnit == null) return -1;
        String u = productUnit.trim().toLowerCase();
        if (isWeightUnit(u)) {
            return (u.equals("g") || u.equals("gm") || u.equals("gram") || u.equals("grams")) ? qty / 1000.0 : qty;
        }
        return -1;
    }

    private double productQtyToL(Integer qty, String productUnit) {
        if (qty == null || productUnit == null) return -1;
        String u = productUnit.trim().toLowerCase();
        if (isVolumeUnit(u)) {
            return (u.equals("ml") || u.equals("millilitre") || u.equals("milliliter")) ? qty / 1000.0 : qty;
        }
        return -1;
    }

    private double toLitre(int qty, String unit) {
        if (unit == null) return -1;
        unit = unit.trim().toLowerCase();
        if (unit.equals("l") || unit.equals("litre") || unit.equals("liter") || unit.equals("litres") || unit.equals("liters")) return qty;
        if (unit.equals("ml") || unit.equals("millilitre") || unit.equals("milliliter")) return qty / 1000.0;
        return -1;
    }

    private boolean isVolumeUnit(String unit) {
        if (unit == null) return false;
        unit = unit.trim().toLowerCase();
        return unit.equals("l") || unit.equals("litre") || unit.equals("liter") || unit.equals("litres") || unit.equals("liters") || unit.equals("ml") || unit.equals("millilitre") || unit.equals("milliliter");
    }

    private Map<String, Object> computeAvailability(InventorySummaryResponse r, int requestedQty, String requestedUnit) {
        Map<String, Object> out = new HashMap<>();
        out.put("productUnit", r.getUnit());
        out.put("productTotalQty", r.getTotalQty());

        String reqUnit = requestedUnit != null ? requestedUnit.trim().toLowerCase() : null;
        double requestedKg = reqUnit != null ? toKg(requestedQty, reqUnit) : -1;
        double requestedL = reqUnit != null ? toLitre(requestedQty, reqUnit) : -1;

        if (requestedKg > 0) {
            if (isWeightUnit(r.getUnit())) {
                out.put("availableInRequestedUnit", r.getTotalQty());
                out.put("availableUnit", "kg");
            } else {
                Double packetKg = parseWeightKgFromText(r.getProductName()) != null ? parseWeightKgFromText(r.getProductName()) : parseWeightKgFromText(r.getProductSku());
                if (packetKg != null) {
                    double availableKg = packetKg * (r.getTotalQty() != null ? r.getTotalQty() : 0);
                    out.put("availableInRequestedUnit", availableKg);
                    out.put("availableUnit", "kg");
                    out.put("availablePackets", (int) Math.floor(availableKg / packetKg));
                } else {
                    out.put("availableInRequestedUnit", null);
                    out.put("availableUnit", "unknown");
                }
            }
            return out;
        }

        if (requestedL > 0) {
            if (isVolumeUnit(r.getUnit())) {
                out.put("availableInRequestedUnit", r.getTotalQty());
                out.put("availableUnit", "l");
            } else {
                Double packetL = parseVolumeLFromText(r.getProductName()) != null ? parseVolumeLFromText(r.getProductName()) : parseVolumeLFromText(r.getProductSku());
                if (packetL != null) {
                    double availableL = packetL * (r.getTotalQty() != null ? r.getTotalQty() : 0);
                    out.put("availableInRequestedUnit", availableL);
                    out.put("availableUnit", "l");
                    out.put("availablePackets", (int) Math.floor(availableL / packetL));
                } else {
                    out.put("availableInRequestedUnit", null);
                    out.put("availableUnit", "unknown");
                }
            }
            return out;
        }

        if (!isWeightUnit(reqUnit) && !isVolumeUnit(reqUnit)) {
            if (r.getUnit() != null && r.getUnit().trim().equalsIgnoreCase(reqUnit)) {
                out.put("availableInRequestedUnit", r.getTotalQty());
                out.put("availableUnit", r.getUnit());
            } else if (isWeightUnit(r.getUnit())) {
                Double packetKg = parseWeightKgFromText(r.getProductName()) != null ? parseWeightKgFromText(r.getProductName()) : parseWeightKgFromText(r.getProductSku());
                double availableKg = productQtyToKg(r.getTotalQty(), r.getUnit());
                if (packetKg != null && availableKg >= 0) {
                    out.put("availablePackets", (int) Math.floor(availableKg / packetKg));
                    out.put("availableInRequestedUnit", Math.floor(availableKg / packetKg));
                    out.put("availableUnit", "packets");
                } else {
                    out.put("availableInRequestedUnit", null);
                    out.put("availableUnit", "unknown");
                }
            } else if (isVolumeUnit(r.getUnit())) {
                Double packetL = parseVolumeLFromText(r.getProductName()) != null ? parseVolumeLFromText(r.getProductName()) : parseVolumeLFromText(r.getProductSku());
                double availableL = productQtyToL(r.getTotalQty(), r.getUnit());
                if (packetL != null && availableL >= 0) {
                    out.put("availablePackets", (int) Math.floor(availableL / packetL));
                    out.put("availableInRequestedUnit", Math.floor(availableL / packetL));
                    out.put("availableUnit", "packets");
                } else {
                    out.put("availableInRequestedUnit", null);
                    out.put("availableUnit", "unknown");
                }
            } else {
                out.put("availableInRequestedUnit", r.getTotalQty());
                out.put("availableUnit", r.getUnit());
            }
            return out;
        }

        out.put("availableInRequestedUnit", r.getTotalQty());
        out.put("availableUnit", r.getUnit());
        return out;
    }

    /* ================= Standard Handlers ================= */

}