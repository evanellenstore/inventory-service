package com.store.inventory.utilty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;

import com.store.inventory.dto.InventorySummaryResponse;

public final class HelperUtilities {

    /**
     * Compute exactly how many items or packets are required.
     * Precision tolerance logic prevents rounding discrepancies on exact measurements.
     */
    public static int calculateRequiredQty(InventorySummaryResponse r, String requestedUnit, int requestedQty,Boolean requestedIsLoose) throws RuntimeException {
    int targetCartQty = 0;


    // Fast-fail for invalid inputs or null response object
    if (r == null || requestedUnit == null || requestedUnit.isEmpty() || requestedQty <= 0) {
        return requestedQty;
    }

    String voiceUnit = requestedUnit.trim().toLowerCase();
    boolean itemIsLoose = Boolean.TRUE.equals(r.isLoose());
    
    if (itemIsLoose) {
        targetCartQty = 1; 
    } else {
        // Safe null/empty checks for packet fields
        String packetUnit = (r.getPacketUnit() != null) ? r.getPacketUnit().trim().toLowerCase() : "";
        Double packetSize = r.getPacketSize();
        if (packetSize == null || packetSize <= 0) {
            return requestedQty; // Fallback to avoid division by zero
        }
       
        // Test packet scenario
        if (voiceUnit.contains("packet") || voiceUnit.contains("pck") || voiceUnit.contains("box")) {
            return requestedQty;
        }

        // Weight packet calculations
        if (isWeightUnit(voiceUnit) && isWeightUnit(packetUnit)) {
            if ("kg".equals(voiceUnit) && "g".equals(packetUnit)) {
                targetCartQty = (int) Math.ceil((requestedQty * 1000.0) / packetSize);
            } else if ("g".equals(voiceUnit) && "kg".equals(packetUnit)) {
                targetCartQty = (int) Math.ceil((requestedQty / 1000.0) / packetSize);
            } else if ("g".equals(voiceUnit) && "g".equals(packetUnit)  && requestedQty % packetSize == 0) {
                targetCartQty = (int) Math.ceil(requestedQty / packetSize);
            } else if ("kg".equals(voiceUnit) && "kg".equals(packetUnit) && requestedQty % packetSize == 0) { 
                    targetCartQty = (int) (requestedQty / packetSize);  
            }else{
                throw new RuntimeException("Quantity mismatch: You requested " + requestedQty + " " + voiceUnit + ", but the packet size is " + packetSize + " " + packetUnit );
            }
        } // Fixed missing closing brace

        // Volume packet calculations
        if (isVolumeUnit(voiceUnit) && isVolumeUnit(packetUnit)) {
            if ("l".equals(voiceUnit) && "ml".equals(packetUnit)) {
                targetCartQty = (int) Math.ceil((requestedQty * 1000.0) / packetSize);
            } else if ("ml".equals(voiceUnit) && "l".equals(packetUnit)) {
                targetCartQty = (int) Math.ceil((requestedQty / 1000.0) / packetSize);
            } else if ("ml".equals(voiceUnit) && "ml".equals(packetUnit) && requestedQty % packetSize == 0) {
                targetCartQty = (int) Math.ceil(requestedQty / packetSize);
            } else if ("l".equals(voiceUnit) && "l".equals(packetUnit) && requestedQty % packetSize == 0) {
                targetCartQty = (int) Math.ceil(requestedQty / packetSize);
            } else{
                throw new RuntimeException("Quantity mismatch: You requested " + requestedQty + " " + voiceUnit + ", but the packet size is " + packetSize + " " + packetUnit);
            } 
        }
    } // Fixed missing closing brace
    
    return targetCartQty;
}

    /**
     * Filters the result list based on row characteristics
     */

    /* 
    public static List<InventorySummaryResponse> filterInventoryByUnit(List<InventorySummaryResponse> resultList,  String requestedUnit, int requestedQty, Boolean requestedIsLoose) {
        
        if (requestedUnit == null || requestedUnit.isEmpty() || requestedQty <= 0) {
            return resultList;
        }
        
        final String voiceUnit = requestedUnit.trim().toLowerCase();

        return resultList.stream()
                .filter(r -> {
                    if (r.getTotalQty() == null) return false;

                    //db loose flag
                    boolean itemIsLoose = r.isLoose() == Boolean.TRUE;

                    if(requestedIsLoose != null && requestedIsLoose && itemIsLoose) { return false;}

                        
                    if (itemIsLoose && requestedIsLoose != null && requestedIsLoose) {
                        String productUnit = r.getUnit() != null ? r.getUnit().trim().toLowerCase() : "";
                        Double productSize = r.getProductSize() != null ? r.getProductSize() : 1.0;
                        
                        if (!productUnit.isEmpty() && productUnit.equalsIgnoreCase(voiceUnit)) {
                            return r.getTotalQty() >= requestedQty;
                        }
                        
                        if (isWeightUnit(voiceUnit) && isWeightUnit(productUnit)) {
                            double totalAvailableKg = r.getTotalQty() * productSize * toKg(1, productUnit);
                            double requestedAmountInKg = toKg(requestedQty, voiceUnit);
                            return totalAvailableKg >= requestedAmountInKg;
                        }
                        
                        if (isVolumeUnit(voiceUnit) && isVolumeUnit(productUnit)) {
                            double totalAvailableL = r.getTotalQty() * productSize * toLitre(1, productUnit);
                            double requestedAmountInL = toLitre(requestedQty, voiceUnit);
                            return totalAvailableL >= requestedAmountInL;
                        }
                    } 
                    else if(requestedIsLoose == null || !requestedIsLoose){
                        int requiredPackets = calculateRequiredQty(r, requestedUnit, requestedQty,requestedIsLoose);
                        if (requiredPackets <= 0) return false; 
                        return r.getTotalQty() >= requiredPackets;
                    }

                    return false;
                })
                .collect(Collectors.toList());
    }*/

            


    public static List<InventorySummaryResponse> filterInventoryByUnit(
            List<InventorySummaryResponse> resultList, String requestedUnit, int requestedQty,
            Boolean requestedIsLoose) {

        // Fail-fast checks
        if (resultList == null || resultList.isEmpty()) {
            return new ArrayList<InventorySummaryResponse>(0);
        }

        List<InventorySummaryResponse> filteredList = null;

        // If requestedIsLoose is null mean first time call
        if (requestedIsLoose == null) {

            if (requestedUnit == null || requestedUnit.isEmpty() || requestedQty <= 0) {
                return resultList;
            }

            final String voiceUnit = requestedUnit.trim().toLowerCase();

            filteredList = new ArrayList<InventorySummaryResponse>();

            for (InventorySummaryResponse r : resultList) {
                if (r == null || r.getTotalQty() == null) {
                    continue;
                }

                // Database packaging state
                boolean itemIsLoose = (r.isLoose() == Boolean.TRUE);

                // Case 1: Evaluate Loose Items
                // (Triggers if explicitly requested loose OR if requestedIsLoose is null)
                if (itemIsLoose) {
                    String productUnit = r.getUnit() != null ? r.getUnit().trim().toLowerCase() : "";
                    Double productSize = r.getProductSize() != null ? r.getProductSize() : 1.0;

                    // Direct Unit Match
                    if (!productUnit.isEmpty() && productUnit.equalsIgnoreCase(voiceUnit)) {
                        if (r.getTotalQty() >= requestedQty) {
                            filteredList.add(r);
                        }
                        continue;
                    }

                    // Weight Unit Conversion Match
                    if (isWeightUnit(voiceUnit) && isWeightUnit(productUnit)) {
                        double totalAvailableKg = r.getTotalQty() * productSize * toKg(1, productUnit);
                        double requestedAmountInKg = toKg(requestedQty, voiceUnit);
                        if (totalAvailableKg >= requestedAmountInKg) {
                            filteredList.add(r);
                        }
                        continue;
                    }

                    // Volume Unit Conversion Match
                    if (isVolumeUnit(voiceUnit) && isVolumeUnit(productUnit)) {
                        double totalAvailableL = r.getTotalQty() * productSize * toLitre(1, productUnit);
                        double requestedAmountInL = toLitre(requestedQty, voiceUnit);
                        if (totalAvailableL >= requestedAmountInL) {
                            filteredList.add(r);
                        }
                        continue;
                    }
                }

                // Case 2: Evaluate Packaged/Packet Items
                // (Triggers if explicitly requested packets OR if requestedIsLoose is null)

                int requiredPackets = calculateRequiredQty(r, requestedUnit, requestedQty, requestedIsLoose);
                if (requiredPackets > 0 && r.getTotalQty() >= requiredPackets) {
                    filteredList.add(r);

                }
            }

        } else /// If requestedIsLoose is true or false mean second time call
        {

            if (requestedUnit == null || requestedUnit.isEmpty() || requestedQty <= 0) {
                return resultList;
            }

            final String voiceUnit = requestedUnit.trim().toLowerCase();

            filteredList = new ArrayList<InventorySummaryResponse>();

            for (InventorySummaryResponse r : resultList) {
                if (r == null || r.getTotalQty() == null) {
                    continue;
                }

                // Database packaging state
                boolean itemIsLoose = (r.isLoose() == Boolean.TRUE);
                boolean requestedIsLooseValue = requestedIsLoose == Boolean.TRUE ? true : false;

                // Case 1: Evaluate Loose Items
                // (Triggers if explicitly requested loose OR if requestedIsLoose is null)
                if (itemIsLoose && requestedIsLooseValue) {
                    String productUnit = r.getUnit() != null ? r.getUnit().trim().toLowerCase() : "";
                    Double productSize = r.getProductSize() != null ? r.getProductSize() : 1.0;

                    // Direct Unit Match
                    if (!productUnit.isEmpty() && productUnit.equalsIgnoreCase(voiceUnit)) {
                        if (r.getTotalQty() >= requestedQty) {
                            filteredList.add(r);
                            break;
                        }
                        continue;
                    }

                    // Weight Unit Conversion Match
                    if (isWeightUnit(voiceUnit) && isWeightUnit(productUnit)) {
                        double totalAvailableKg = r.getTotalQty() * productSize * toKg(1, productUnit);
                        double requestedAmountInKg = toKg(requestedQty, voiceUnit);
                        if (totalAvailableKg >= requestedAmountInKg) {
                            filteredList.add(r);
                            break;
                        }
                        continue;
                    }

                    // Volume Unit Conversion Match
                    if (isVolumeUnit(voiceUnit) && isVolumeUnit(productUnit)) {
                        double totalAvailableL = r.getTotalQty() * productSize * toLitre(1, productUnit);
                        double requestedAmountInL = toLitre(requestedQty, voiceUnit);
                        if (totalAvailableL >= requestedAmountInL) {
                            filteredList.add(r);
                            break;
                        }
                        continue;
                    }
                } else if (!itemIsLoose && !requestedIsLooseValue) {
                    // Case 2: Evaluate Packaged/Packet Items
                    // (Triggers if explicitly requested packets OR if requestedIsLoose is null)

                    int requiredPackets = calculateRequiredQty(r, requestedUnit, requestedQty, requestedIsLoose);
                    if (requiredPackets > 0 && r.getTotalQty() >= requiredPackets) {
                        filteredList.add(r);
                        break;

                    }

                }

            }

        }

        return filteredList;
    }          




   


    public static Map<String, Object> buildCandidateMap(InventorySummaryResponse r) {
        String productName = r.getProductName() != null ? r.getProductName().toLowerCase() : "";
        boolean itemIsLoose = productName.contains("loose") || 
                             r.getPacketSize() == null || 
                             r.getPacketSize() <= 0 || 
                             r.getPacketUnit() == null;

        return Map.of(
            "productId", r.getProductId() != null ? r.getProductId() : 0L,
            "productSku", r.getProductSku() != null ? r.getProductSku() : "",
            "productName", r.getProductName() != null ? r.getProductName() : "",
            "brand", r.getBrandName() != null ? r.getBrandName() : "",
            "unit", r.getUnit() != null ? r.getUnit() : "",
            "totalQty", r.getTotalQty() != null ? r.getTotalQty() : 0,
            "price", r.getPrice() != null ? r.getPrice() : 0.0,
            "discountAmount", r.getDiscountAmount() != null ? r.getDiscountAmount() : 0.0,
            "isLoose", itemIsLoose
        );
    }

    public static boolean isWeightUnit(String unit) {
        if (unit == null) return false;
        String cleanUnit = unit.trim().toLowerCase();
        return cleanUnit.endsWith("kg") || 
               cleanUnit.endsWith("gm") || 
               cleanUnit.endsWith("g") || 
               cleanUnit.contains("kilogram") || 
               cleanUnit.contains("gram");
    }

    public static boolean isVolumeUnit(String unit) {
        if (unit == null) return false;
        String cleanUnit = unit.trim().toLowerCase();
        return cleanUnit.endsWith("l") || 
               cleanUnit.endsWith("ltr") || 
               cleanUnit.endsWith("ml") || 
               cleanUnit.contains("liter") || 
               cleanUnit.contains("litre") || 
               cleanUnit.contains("milliliter");
    }

    public static double toKg(double qty, String unit) {
        if (unit == null) return 0;
        String cleanUnit = unit.trim().toLowerCase();
        if (cleanUnit.endsWith("gm") || cleanUnit.endsWith("g") || cleanUnit.contains("gram")) {
            return qty / 1000.0;
        }
        if (cleanUnit.endsWith("kg") || cleanUnit.contains("kilogram")) {
            return qty;
        }
        return 0;
    }

    public static double toLitre(double qty, String unit) {
        if (unit == null) return 0;
        String cleanUnit = unit.trim().toLowerCase();
        if (cleanUnit.endsWith("ml") || cleanUnit.contains("milliliter")) {
            return qty / 1000.0;
        }
        if (cleanUnit.endsWith("l") || cleanUnit.endsWith("ltr") || cleanUnit.contains("liter") || cleanUnit.contains("litre")) {
            return qty;
        }
        return 0;
    }

    public static ResponseEntity<Map<String, Object>> buildMultiBrandResponse(List<String> brands, List<InventorySummaryResponse> candidates, int qty, String unit) {
        Map<String, Object> body = new HashMap<>();
        body.put("prompt", buildBrandPrompt(brands));
        body.put("options", brands);
        body.put("multiBrand", true);
        body.put("quantityIgnored", true);
        body.put("requestedQty", qty);
        body.put("requestedUnit", unit);
        body.put("candidates", candidates.stream().map(HelperUtilities::buildCandidateMap).collect(Collectors.toList()));
        return ResponseEntity.ok(body);
    }

    public static String buildBrandPrompt(List<String> brands) {
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
}