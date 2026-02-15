package com.cmpe273.order.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final WebClient inventoryClient;
    private final WebClient notificationClient;

    public OrderController(
            @Qualifier("inventoryClient") WebClient inventoryClient,
            @Qualifier("notificationClient") WebClient notificationClient
    ) {
        this.inventoryClient = inventoryClient;
        this.notificationClient = notificationClient;
    }

    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody Map<String, Object> request) {

        String orderId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();

        String userId = (String) request.get("user_id");
        String itemId = (String) request.get("item_id");
        Integer qty = (Integer) request.get("qty");

        if (userId == null || itemId == null || qty == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "order_id", orderId,
                    "status", "FAILED",
                    "error", "INVALID_REQUEST",
                    "ts", Instant.now().toString()
            ));
        }

        // 1) Call Inventory synchronously: POST /reserve
        try {
            long invStart = System.currentTimeMillis();

            Map<String, Object> invResp = inventoryClient.post()
                    .uri("/reserve")
                    .bodyValue(Map.of(
                            "order_id", orderId,
                            "item_id", itemId,
                            "qty", qty
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(); // <-- synchronous behavior

            long invLatency = System.currentTimeMillis() - invStart;

            // 2) Call Notification synchronously: POST /send
            long notifStart = System.currentTimeMillis();

            notificationClient.post()
                    .uri("/send")
                    .bodyValue(Map.of(
                            "order_id", orderId,
                            "user_id", userId,
                            "message", "Order confirmed"
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            long notifLatency = System.currentTimeMillis() - notifStart;
            long total = System.currentTimeMillis() - start;

            System.out.println("[OrderService] OK order_id=" + orderId +
                    " inv_ms=" + invLatency + " notif_ms=" + notifLatency + " total_ms=" + total);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "order_id", orderId,
                    "status", "CONFIRMED",
                    "ts", Instant.now().toString()
            ));

        } catch (WebClientResponseException wcre) {
            
            int status = wcre.getStatusCode().value();

            // Inventory out of stock -> 409
            if (status == 409) {
                return ResponseEntity.status(409).body(Map.of(
                        "order_id", orderId,
                        "status", "FAILED",
                        "error", "OUT_OF_STOCK",
                        "ts", Instant.now().toString()
                ));
            }

            // Inventory/Notification 5xx -> 502
            return ResponseEntity.status(502).body(Map.of(
                    "order_id", orderId,
                    "status", "FAILED",
                    "error", "DOWNSTREAM_ERROR",
                    "downstream_status", status,
                    "ts", Instant.now().toString()
            ));

                } catch (Exception ex) {

            // Walk the cause chain to detect timeouts reliably
            Throwable t = ex;
            while (t != null) {
                String cn = t.getClass().getName();

                // Common timeout exceptions with WebClient/Reactor Netty
                if (cn.contains("TimeoutException") ||
                    cn.contains("ReadTimeoutException") ||
                    cn.contains("WriteTimeoutException") ||
                    cn.contains("PrematureCloseException")) {

                    return ResponseEntity.status(504).body(Map.of(
                            "order_id", orderId,
                            "status", "FAILED",
                            "error", "INVENTORY_TIMEOUT",
                            "ts", Instant.now().toString()
                    ));
                }
                t = t.getCause();
            }

            // Helpful debug log 
            System.out.println("[OrderService] ERROR order_id=" + orderId + " ex=" + ex.getClass() + " msg=" + ex.getMessage());

            return ResponseEntity.status(500).body(Map.of(
                    "order_id", orderId,
                    "status", "FAILED",
                    "error", "INTERNAL_ERROR",
                    "ts", Instant.now().toString()
            ));
        }
    }
}

