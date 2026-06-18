package com.store.inventory.client;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import com.store.inventory.dto.ProductResponse;

@FeignClient(name = "product-service", url = "http://localhost:2014")
public interface ProductServiceClient {

     @GetMapping("/products/{id}")
    public ProductResponse getById(@PathVariable Long id);

    @GetMapping("/products/search/name")
    public List<ProductResponse> getByName(@RequestParam("name") String name, @RequestParam("language") String language);

}
