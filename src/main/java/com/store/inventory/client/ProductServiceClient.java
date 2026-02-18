package com.store.inventory.client;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.store.inventory.dto.ProductResponse;
@FeignClient(name = "product-service", url = "http://localhost:2014")
public interface ProductServiceClient {

     @GetMapping("/products/{id}")
    public ProductResponse getById(@PathVariable Long id);
        

}
