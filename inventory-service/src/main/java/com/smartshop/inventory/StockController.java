package com.smartshop.inventory;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/stock")
public class StockController {

    private final StockRepository repository;

    public StockController(StockRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{sku}")
    public Stock get(@PathVariable String sku) {
        return repository.findById(sku)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No stock record for SKU " + sku));
    }

    @PutMapping("/{sku}")
    public Stock put(@PathVariable String sku, @Valid @RequestBody UpdateStockRequest request) {
        Stock stock = repository.findById(sku).orElseGet(() -> new Stock(sku, 0));
        stock.setQuantity(request.quantity());
        return repository.save(stock);
    }
}