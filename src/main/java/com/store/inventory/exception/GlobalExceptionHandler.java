package com.store.inventory.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InventoryException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleInventoryException(InventoryException ex) {
        return ex.getMessage();
    }
}
