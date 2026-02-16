package com.cmpe273.inventory.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class InventoryController {

    private final Map<String, Integer> stock = new ConcurrentHashMap<>();

    @Value("${INVENTORY_DELAY_MS:0}")
    private long delayMs;

    @Value("${INVENTORY_FAIL:false}")
    private boolean forceFail;

    public InventoryController() {
        stock.put("burrito", 1000);
        stock.put("pizza", 1000);
    }

    @PostMapping("/reserve")
    public ResponseEntity<?> reserve(@RequestBody Map<String, Object> request) throws InterruptedException {

        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }

        if (forceFail) {
            return ResponseEntity.status(500)
                    .body(Map.of("status", "FAILED", "reason", "FORCED_FAILURE"));
        }

        String itemId = (String) request.get("item_id");
        Integer qty = (Integer) request.get("qty");

        if (!stock.containsKey(itemId) || stock.get(itemId) < qty) {
            return ResponseEntity.status(409)
                    .body(Map.of("status", "FAILED", "reason", "OUT_OF_STOCK"));
        }

        stock.put(itemId, stock.get(itemId) - qty);

        return ResponseEntity.ok(
                Map.of(
                        "reservation_id", UUID.randomUUID().toString(),
                        "status", "RESERVED"
                )
        );
    }

    @GetMapping("/inventory")
    public ResponseEntity<Map<String, Integer>> getAllInventory() {
        return ResponseEntity.ok(stock);
    }
}
