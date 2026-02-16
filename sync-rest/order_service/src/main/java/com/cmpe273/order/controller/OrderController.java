package com.cmpe273.order.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final WebClient inventoryClient;
    private final WebClient notificationClient;
    private final Map<String, Map<String, Object>> orders = new ConcurrentHashMap<>();

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
                    "message", "Missing required fields: user_id, item_id, and qty are required.",
                    "ts", Instant.now().toString()
            ));
        }

        // 1) Call Inventory synchronously: POST /reserve
        long invStart = System.currentTimeMillis();
        try {
            Map<String, Object> invResp = inventoryClient.post()
                    .uri("/reserve")
                    .bodyValue(Map.of(
                            "order_id", orderId,
                            "item_id", itemId,
                            "qty", qty
                    ))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (WebClientResponseException wcre) {
            int status = wcre.getStatusCode().value();
            if (status == 409) {
                storeFailedOrder(orderId, userId, itemId, qty, "FAILED", "OUT_OF_STOCK", null);
                return ResponseEntity.status(409).body(failureBody(orderId, "OUT_OF_STOCK",
                        "Item out of stock or insufficient quantity.", null));
            }
            storeFailedOrder(orderId, userId, itemId, qty, "FAILED", "DOWNSTREAM_ERROR", status);
            return ResponseEntity.status(502).body(failureBody(orderId, "DOWNSTREAM_ERROR",
                    "Inventory service returned an error.", status));
        } catch (Exception ex) {
            ResponseEntity<?> invFailure = handleDownstreamFailure(orderId, userId, itemId, qty, "inventory", ex);
            if (invFailure != null) return invFailure;
        }

        long invLatency = System.currentTimeMillis() - invStart;

        // 2) Call Notification synchronously: POST /send
        long notifStart = System.currentTimeMillis();
        try {
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
        } catch (WebClientResponseException wcre) {
            int status = wcre.getStatusCode().value();
            storeFailedOrder(orderId, userId, itemId, qty, "FAILED", "DOWNSTREAM_ERROR", status);
            return ResponseEntity.status(502).body(failureBody(orderId, "DOWNSTREAM_ERROR",
                    "Notification service returned an error.", status));
        } catch (Exception ex) {
            ResponseEntity<?> notifFailure = handleDownstreamFailure(orderId, userId, itemId, qty, "notification", ex);
            if (notifFailure != null) return notifFailure;
        }

        long notifLatency = System.currentTimeMillis() - notifStart;
        long total = System.currentTimeMillis() - start;

        System.out.println("[OrderService] OK order_id=" + orderId +
                " inv_ms=" + invLatency + " notif_ms=" + notifLatency + " total_ms=" + total);

        Map<String, Object> orderData = new LinkedHashMap<>();
        orderData.put("order_id", orderId);
        orderData.put("user_id", userId);
        orderData.put("item_id", itemId);
        orderData.put("qty", qty);
        orderData.put("status", "CONFIRMED");
        orderData.put("ts", Instant.now().toString());
        orderData.put("inventory_latency_ms", invLatency);
        orderData.put("notification_latency_ms", notifLatency);
        orderData.put("total_latency_ms", total);
        orders.put(orderId, orderData);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "order_id", orderId,
                "status", "CONFIRMED",
                "ts", Instant.now().toString()
        ));
    }

    private Map<String, Object> failureBody(String orderId, String errorCode, String message, Integer downstreamStatus) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", orderId);
        body.put("status", "FAILED");
        body.put("error", errorCode);
        body.put("message", message);
        body.put("ts", Instant.now().toString());
        if (downstreamStatus != null) body.put("downstream_status", downstreamStatus);
        return body;
    }

    private void storeFailedOrder(String orderId, String userId, String itemId, Integer qty,
                                   String status, String error, Integer downstreamStatus) {
        Map<String, Object> orderData = new LinkedHashMap<>();
        orderData.put("order_id", orderId);
        orderData.put("user_id", userId != null ? userId : "unknown");
        orderData.put("item_id", itemId != null ? itemId : "unknown");
        orderData.put("qty", qty != null ? qty : 0);
        orderData.put("status", status);
        orderData.put("error", error);
        orderData.put("ts", Instant.now().toString());
        if (downstreamStatus != null) orderData.put("downstream_status", downstreamStatus);
        orders.put(orderId, orderData);
    }

    /** Handles connection failures, timeouts, and other errors when calling a downstream service. */
    private ResponseEntity<?> handleDownstreamFailure(String orderId, String userId, String itemId, Integer qty,
                                                       String serviceName, Exception ex) {
        String serviceDownMessage = serviceName + " service is down or unreachable. Please try again later.";
        Throwable t = ex;
        while (t != null) {
            String cn = t.getClass().getName();
            String msg = t.getMessage() != null ? t.getMessage() : "";
            String msgLower = msg.toLowerCase();

            if (cn.contains("TimeoutException") || cn.contains("ReadTimeoutException") ||
                cn.contains("WriteTimeoutException") || cn.contains("PrematureCloseException")) {
                storeFailedOrder(orderId, userId, itemId, qty, "FAILED", serviceName.toUpperCase() + "_TIMEOUT", null);
                return ResponseEntity.status(504).body(failureBody(orderId, serviceName.toUpperCase() + "_TIMEOUT",
                        serviceName + " service did not respond in time. Please try again later.", null));
            }
            // Connection refused, host unreachable, DNS failure, or any "service down" style error
            if (cn.contains("ConnectException") || cn.contains("ConnectionRefused") ||
                cn.contains("ConnectTimeoutException") || cn.contains("UnknownHostException") ||
                msgLower.contains("connection refused") || msgLower.contains("connection reset") ||
                msgLower.contains("no route to host") || msgLower.contains("host is unreachable") ||
                msgLower.contains("unable to connect") || msgLower.contains("cannot connect") ||
                msg.contains("No route to host")) {
                storeFailedOrder(orderId, userId, itemId, qty, "FAILED", "SERVICE_UNAVAILABLE", null);
                return ResponseEntity.status(503).body(failureBody(orderId, "SERVICE_UNAVAILABLE",
                        serviceDownMessage, null));
            }
            t = t.getCause();
        }

        System.out.println("[OrderService] ERROR order_id=" + orderId + " service=" + serviceName +
                " ex=" + ex.getClass().getName() + " msg=" + ex.getMessage());

        // Any other failure (e.g. unexpected exception) -> still return 503 with clear message, never INTERNAL_ERROR to client
        storeFailedOrder(orderId, userId, itemId, qty, "FAILED", "SERVICE_UNAVAILABLE", null);
        return ResponseEntity.status(503).body(failureBody(orderId, "SERVICE_UNAVAILABLE",
                serviceDownMessage, null));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllOrders() {
        return ResponseEntity.ok(new ArrayList<>(orders.values()));
    }
}

