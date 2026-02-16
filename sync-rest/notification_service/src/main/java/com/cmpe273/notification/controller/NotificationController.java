package com.cmpe273.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class NotificationController {

    private final Map<String, Map<String, Object>> notifications = new ConcurrentHashMap<>();

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestBody Map<String, Object> request) {
        System.out.println("[NotificationService] send called: " + request);
        
        String notificationId = UUID.randomUUID().toString();
        String orderId = (String) request.get("order_id");
        String userId = (String) request.get("user_id");
        String message = (String) request.get("message");
        
        // Store the notification
        Map<String, Object> notificationData = Map.of(
                "notification_id", notificationId,
                "order_id", orderId != null ? orderId : "unknown",
                "user_id", userId != null ? userId : "unknown",
                "message", message != null ? message : "unknown",
                "status", "SENT",
                "ts", Instant.now().toString()
        );
        notifications.put(notificationId, notificationData);
        
        return ResponseEntity.ok(Map.of("status", "SENT", "notification_id", notificationId));
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<Map<String, Object>>> getAllNotifications() {
        return ResponseEntity.ok(new ArrayList<>(notifications.values()));
    }
}
