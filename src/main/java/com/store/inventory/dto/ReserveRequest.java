package com.store.inventory.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ReserveRequest {
    private Integer quantity;
    private String referenceId;
    
}
