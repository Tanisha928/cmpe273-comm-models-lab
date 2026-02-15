package com.cmpe273.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/send")
public class NotificationController {

    @PostMapping
    public ResponseEntity<?> send(@RequestBody Map<String, Object> request) {
        System.out.println("[NotificationService] send called: " + request);
        return ResponseEntity.ok(Map.of("status", "SENT"));
    }
}
