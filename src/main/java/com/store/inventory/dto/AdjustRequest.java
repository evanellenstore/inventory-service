package com.store.inventory.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AdjustRequest {
    private Integer quantity;
    private String type;   
    private String remarks;
}
