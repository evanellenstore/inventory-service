package com.store.inventory.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @GetMapping()
    public List<InventorySummaryResponse> getAll(
            @RequestParam(required = false) String stockStatus,
            @RequestParam(required = false) String expiryStatus) {
        
        if ((stockStatus != null && !stockStatus.isEmpty()) || 
            (expiryStatus != null && !expiryStatus.isEmpty())) {
            return inventoryService.getInventoryFiltered(stockStatus, expiryStatus);
        }
        return inventoryService.getAllInventory();
    }

    //================for voice integration===========================
    @PostMapping("/search")
    public ResponseEntity<?> searchByName(@RequestBody Map<String, String> request) {
        String language = request.get("language");
        String textJson = request.get("text");
        ObjectMapper mapper = new ObjectMapper();
        VoiceCommand command;
        try {
            command = mapper.readValue(textJson, VoiceCommand.class);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid voice command", "details", e.getMessage()));
        }

        int requestedQty = command.getQty() != null ? command.getQty() : 0;
        String requestedUnit = command.getUnit();

        List<InventorySummaryResponse> resultList = inventoryService.searchByProductName(command.getProductName(), language);
        // If client provided an explicit brand, filter server-side to that brand
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

        // If multiple brands exist in the raw results, return brand-disambiguation first and ignore quantity
        List<String> rawBrands = resultList.stream()
            .map(InventorySummaryResponse::getBrandName)
            .filter(b -> b != null && !b.isEmpty())
            .distinct()
            .collect(Collectors.toList());
            
        if (rawBrands.size() > 1) {
            String prompt = buildBrandPrompt(rawBrands);
            Map<String, Object> body = new HashMap<>();
            body.put("prompt", prompt);
            body.put("options", rawBrands);
            body.put("multiBrand", true);
            body.put("quantityIgnored", true);
            body.put("requestedQty", requestedQty);
            body.put("requestedUnit", requestedUnit);
            body.put("candidates", resultList.stream()
                .map(r -> Map.of(
                    "productId", r.getProductId(),
                    "productSku", r.getProductSku(),
                    "productName", r.getProductName(),
                    "brand", r.getBrandName(),
                    "unit", r.getUnit(),
                    "totalQty", r.getTotalQty(),
                    "price", r.getPrice(),
                    "discountAmount", r.getDiscountAmount()))
                .collect(Collectors.toList()));
            return ResponseEntity.ok(body);
        }

        List<InventorySummaryResponse> filtered = resultList;
        if (requestedUnit != null && !requestedUnit.isEmpty() && requestedQty > 0) {
            final String voiceUnit = requestedUnit.trim().toLowerCase();
            final double requestedAmountInKg = toKg(requestedQty, voiceUnit); // -1 if not a weight unit
            final double requestedAmountInL = toLitre(requestedQty, voiceUnit); // -1 if not a volume unit

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
                            if (packetKg == null) {
                                return false;
                            }
                            int requiredPackets = (int) Math.ceil(requestedAmountInKg / packetKg);
                            return r.getTotalQty() >= requiredPackets;
                        }

                        if (requestedAmountInL > 0 && !isVolumeUnit(productUnit)) {
                            Double packetL = parseVolumeLFromText(r.getProductName());
                            if (packetL == null) packetL = parseVolumeLFromText(r.getProductSku());
                            if (packetL == null) {
                                return false;
                            }
                            int requiredPackets = (int) Math.ceil(requestedAmountInL / packetL);
                            return r.getTotalQty() >= requiredPackets;
                        }

                        if (requestedAmountInKg <= 0 && isWeightUnit(productUnit)) {
                            Double packetKg = parseWeightKgFromText(r.getProductName());
                            if (packetKg == null) packetKg = parseWeightKgFromText(r.getProductSku());
                            if (packetKg == null) {
                                return false;
                            }
                            double availableKg = productQtyToKg(r.getTotalQty(), productUnit);
                            double requiredKg = requestedQty * packetKg;
                            return availableKg >= requiredKg;
                        }

                        if (requestedAmountInL <= 0 && isVolumeUnit(productUnit)) {
                            Double packetL = parseVolumeLFromText(r.getProductName());
                            if (packetL == null) packetL = parseVolumeLFromText(r.getProductSku());
                            if (packetL == null) {
                                return false;
                            }
                            double availableL = productQtyToL(r.getTotalQty(), productUnit);
                            double requiredL = requestedQty * packetL;
                            return availableL >= requiredL;
                        }

                        return false;
                    })
                    .collect(Collectors.toList());
        }

        // If exactly one candidate, return detailed info including requested vs available quantities
        if (filtered.size() == 1) {
            InventorySummaryResponse r = filtered.get(0);
            Map<String, Object> avail = computeAvailability(r, requestedQty, requestedUnit);
            Map<String, Object> body = new HashMap<>();
            body.put("requestedQty", requestedQty);
            body.put("requestedUnit", requestedUnit);
            body.put("multiBrand", false);
                body.put("candidate", Map.of(
                    "productId", r.getProductId(),
                    "productSku", r.getProductSku(),
                    "productName", r.getProductName(),
                    "brand", r.getBrandName(),
                    "unit", r.getUnit(),
                    "totalQty", r.getTotalQty(),
                    "price", r.getPrice(),
                    "discountAmount", r.getDiscountAmount()
                ));
            body.put("availability", avail);
            return ResponseEntity.ok(body);
        }

        // If filtering removed all but original had multiple, prepare a brand-disambiguation prompt (ignore quantity)
        if (filtered.isEmpty() && resultList.size() > 1) {
            List<String> brands = resultList.stream()
                    .map(InventorySummaryResponse::getBrandName)
                    .filter(b -> b != null && !b.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
            if (brands.size() <= 1) {
                Map<String, Object> body = new HashMap<>();
                body.put("multiBrand", false);
                body.put("candidates", resultList.stream()
                        .map(r -> Map.of(
                                "productId", r.getProductId(),
                                "productSku", r.getProductSku(),
                                "productName", r.getProductName(),
                                "brand", r.getBrandName(),
                                "unit", r.getUnit(),
                                "totalQty", r.getTotalQty()))
                        .collect(Collectors.toList()));
                return ResponseEntity.ok(body);
            }
            String prompt = buildBrandPrompt(brands);
            Map<String, Object> body = new HashMap<>();
            body.put("prompt", prompt);
            body.put("options", brands);
            body.put("multiBrand", true);
            body.put("quantityIgnored", true);
            body.put("candidates", resultList.stream()
                        .map(r -> Map.of(
                            "productId", r.getProductId(),
                            "productSku", r.getProductSku(),
                            "productName", r.getProductName(),
                            "brand", r.getBrandName(),
                            "unit", r.getUnit(),
                            "totalQty", r.getTotalQty(),
                            "price", r.getPrice(),
                            "discountAmount", r.getDiscountAmount()))
                    .collect(Collectors.toList()));
            return ResponseEntity.ok(body);
        }

        // If multiple candidates remain after filtering, ask user to pick a brand (also ignore quantity for this phase)
        if (filtered.size() > 1) {
            List<String> brands = filtered.stream()
                    .map(InventorySummaryResponse::getBrandName)
                    .filter(b -> b != null && !b.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
            String prompt = buildBrandPrompt(brands);
            Map<String, Object> body = new HashMap<>();
            body.put("prompt", prompt);
            body.put("options", brands);
                body.put("multiBrand", true);
            body.put("quantityIgnored", true);
                body.put("candidates", filtered.stream()
                    .map(r -> Map.of(
                        "productId", r.getProductId(),
                        "productSku", r.getProductSku(),
                        "productName", r.getProductName(),
                        "brand", r.getBrandName(),
                        "unit", r.getUnit(),
                        "totalQty", r.getTotalQty(),
                        "price", r.getPrice(),
                        "discountAmount", r.getDiscountAmount()))
                    .collect(Collectors.toList()));
            return ResponseEntity.ok(body);
        }

        // Otherwise return whatever candidates are left (zero or one)
        return ResponseEntity.ok(filtered);
    }

    private String buildBrandPrompt(List<String> brands) {
        if (brands == null || brands.isEmpty()) {
            return "Multiple products found. Which brand do you want?";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Multiple brands found: ");
        for (int i = 0; i < brands.size(); i++) {
            sb.append(i + 1).append(". ").append(brands.get(i));
            if (i < brands.size() - 1) sb.append(", ");
        }
        sb.append(". Which brand do you want?");
        return sb.toString();
    }

    // Convert qty+unit to kilograms. Returns -1 when unit is not a weight unit.
    private double toKg(int qty, String unit) {
        if (unit == null) return -1;
        unit = unit.trim().toLowerCase();
        try {
            if (unit.equals("kg") || unit.equals("kilogram") || unit.equals("kilograms")) {
                return qty;
            }
            if (unit.equals("g") || unit.equals("gram") || unit.equals("grams") || unit.equals("gm")) {
                return qty / 1000.0;
            }
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    private boolean isWeightUnit(String unit) {
        if (unit == null) return false;
        unit = unit.trim().toLowerCase();
        return unit.equals("kg") || unit.equals("kilogram") || unit.equals("kilograms") || unit.equals("g") || unit.equals("gram") || unit.equals("grams") || unit.equals("gm");
    }

    // Try to extract weight in kilograms from free text like product name or sku. Returns null if not found.
    private Double parseWeightKgFromText(String text) {
        if (text == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|g|gm|gram|grams)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1));
                String u = m.group(2).toLowerCase();
                if (u.equals("g") || u.equals("gm") || u.equals("gram") || u.equals("grams")) {
                    return val / 1000.0;
                }
                return val; // kg
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    // Parse volume in litres from free text like "500ml", "0.5 l", "2L". Returns null if not found.
    private Double parseVolumeLFromText(String text) {
        if (text == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(l|litre|liter|ml|millilitre|milliliter)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1));
                String u = m.group(2).toLowerCase();
                if (u.equals("ml") || u.equals("millilitre") || u.equals("milliliter")) {
                    return val / 1000.0;
                }
                return val; // litres
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    // Convert product quantity expressed in productUnit to kilograms. Returns -1 if productUnit is not weight.
    private double productQtyToKg(Integer qty, String productUnit) {
        if (qty == null || productUnit == null) return -1;
        String u = productUnit.trim().toLowerCase();
        if (isWeightUnit(u)) {
            if (u.equals("g") || u.equals("gm") || u.equals("gram") || u.equals("grams")) {
                return qty / 1000.0;
            }
            return qty; // kg
        }
        return -1;
    }

    // Convert product quantity expressed in productUnit to litres. Returns -1 if productUnit is not volume.
    private double productQtyToL(Integer qty, String productUnit) {
        if (qty == null || productUnit == null) return -1;
        String u = productUnit.trim().toLowerCase();
        if (isVolumeUnit(u)) {
            if (u.equals("ml") || u.equals("millilitre") || u.equals("milliliter")) {
                return qty / 1000.0;
            }
            return qty; // litres
        }
        return -1;
    }

    // Convert qty+unit to litres. Returns -1 when unit is not a volume unit.
    private double toLitre(int qty, String unit) {
        if (unit == null) return -1;
        unit = unit.trim().toLowerCase();
        try {
            if (unit.equals("l") || unit.equals("litre") || unit.equals("liter") || unit.equals("litres") || unit.equals("liters")) {
                return qty;
            }
            if (unit.equals("ml") || unit.equals("millilitre") || unit.equals("milliliter")) {
                return qty / 1000.0;
            }
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    private boolean isVolumeUnit(String unit) {
        if (unit == null) return false;
        unit = unit.trim().toLowerCase();
        return unit.equals("l") || unit.equals("litre") || unit.equals("liter") || unit.equals("litres") || unit.equals("liters") || unit.equals("ml") || unit.equals("millilitre") || unit.equals("milliliter");
    }

    // Compute availability of product `r` expressed in requested unit/qty terms.
    private Map<String, Object> computeAvailability(InventorySummaryResponse r, int requestedQty, String requestedUnit) {
        Map<String, Object> out = new HashMap<>();
        out.put("productUnit", r.getUnit());
        out.put("productTotalQty", r.getTotalQty());

        String reqUnit = requestedUnit != null ? requestedUnit.trim().toLowerCase() : null;
        double requestedKg = reqUnit != null ? toKg(requestedQty, reqUnit) : -1;
        double requestedL = reqUnit != null ? toLitre(requestedQty, reqUnit) : -1;

        // If requested is weight
        if (requestedKg > 0) {
            if (isWeightUnit(r.getUnit())) {
                // product totalQty is in kg
                out.put("availableInRequestedUnit", r.getTotalQty());
                out.put("availableUnit", "kg");
            } else {
                // product sold in packets -> parse packet weight
                Double packetKg = parseWeightKgFromText(r.getProductName());
                if (packetKg == null) packetKg = parseWeightKgFromText(r.getProductSku());
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

        // If requested is volume
        if (requestedL > 0) {
            if (isVolumeUnit(r.getUnit())) {
                out.put("availableInRequestedUnit", r.getTotalQty());
                out.put("availableUnit", "l");
            } else {
                Double packetL = parseVolumeLFromText(r.getProductName());
                if (packetL == null) packetL = parseVolumeLFromText(r.getProductSku());
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

        // Requested is a count/packets (fallback)
        if (!isWeightUnit(reqUnit) && !isVolumeUnit(reqUnit)) {
            // If product unit is same (packets or count), return totalQty
            if (r.getUnit() != null && r.getUnit().trim().equalsIgnoreCase(reqUnit)) {
                out.put("availableInRequestedUnit", r.getTotalQty());
                out.put("availableUnit", r.getUnit());
            } else if (isWeightUnit(r.getUnit())) {
                // product stored in weight, parse packet weight to convert
                Double packetKg = parseWeightKgFromText(r.getProductName());
                if (packetKg == null) packetKg = parseWeightKgFromText(r.getProductSku());
                if (packetKg != null) {
                    double availableKg = productQtyToKg(r.getTotalQty(), r.getUnit());
                    if (availableKg >= 0) {
                        out.put("availablePackets", (int) Math.floor(availableKg / packetKg));
                        out.put("availableInRequestedUnit", Math.floor(availableKg / packetKg));
                        out.put("availableUnit", "packets");
                    } else {
                        out.put("availableInRequestedUnit", null);
                        out.put("availableUnit", "unknown");
                    }
                } else {
                    out.put("availableInRequestedUnit", null);
                    out.put("availableUnit", "unknown");
                }
            } else if (isVolumeUnit(r.getUnit())) {
                Double packetL = parseVolumeLFromText(r.getProductName());
                if (packetL == null) packetL = parseVolumeLFromText(r.getProductSku());
                if (packetL != null) {
                    double availableL = productQtyToL(r.getTotalQty(), r.getUnit());
                    if (availableL >= 0) {
                        out.put("availablePackets", (int) Math.floor(availableL / packetL));
                        out.put("availableInRequestedUnit", Math.floor(availableL / packetL));
                        out.put("availableUnit", "packets");
                    } else {
                        out.put("availableInRequestedUnit", null);
                        out.put("availableUnit", "unknown");
                    }
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

        // default fallback
        out.put("availableInRequestedUnit", r.getTotalQty());
        out.put("availableUnit", r.getUnit());
        return out;
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
