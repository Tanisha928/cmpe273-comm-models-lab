package com.cmpe273.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ProducerOrder {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String TOPIC = "order_events";
    
    public static void main(String[] args) {
        String bootstrapServers = System.getenv().getOrDefault("BOOTSTRAP_SERVERS", "localhost:9092");
        String eventsStr = System.getenv().get("EVENTS");
        int events = (eventsStr == null || eventsStr.trim().isEmpty()) ? 1000 : Integer.parseInt(eventsStr.trim());
        String ratePerSecStr = System.getenv().get("RATE_PER_SEC");
        Integer ratePerSec = (ratePerSecStr != null && !ratePerSecStr.trim().isEmpty()) ? Integer.parseInt(ratePerSecStr.trim()) : null;
        
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        
        long startTime = System.currentTimeMillis();
        Random random = new Random();
        
        // Spread timestamps across 10 minutes for realistic analytics
        Instant baseTime = Instant.now().minus(10, ChronoUnit.MINUTES);
        long totalSeconds = 600; // 10 minutes
        long intervalMs = totalSeconds * 1000 / events;
        
        int produced = 0;
        long lastThrottleCheck = System.currentTimeMillis();
        int producedThisSecond = 0;
        
        try {
            for (int i = 0; i < events; i++) {
                // Throttle if rate limit specified
                if (ratePerSec != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastThrottleCheck >= 1000) {
                        producedThisSecond = 0;
                        lastThrottleCheck = now;
                    }
                    if (producedThisSecond >= ratePerSec) {
                        Thread.sleep(1000 - (now - lastThrottleCheck));
                        producedThisSecond = 0;
                        lastThrottleCheck = System.currentTimeMillis();
                    }
                }
                
                // Create event with spread timestamp
                Instant eventTime = baseTime.plusSeconds(i * totalSeconds / events);
                String eventId = UUID.randomUUID().toString();
                String orderId = UUID.randomUUID().toString();
                String userId = "user_" + random.nextInt(1000);
                String itemId = random.nextBoolean() ? "burrito" : "pizza";
                int qty = random.nextInt(10) + 1;
                
                ObjectNode event = mapper.createObjectNode();
                event.put("type", "OrderPlaced");
                event.put("event_id", eventId);
                event.put("order_id", orderId);
                event.put("user_id", userId);
                event.put("item_id", itemId);
                event.put("qty", qty);
                event.put("created_at", eventTime.toString());
                
                String eventJson = mapper.writeValueAsString(event);
                ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, orderId, eventJson);
                
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        logError("Failed to send event", eventId, orderId, exception);
                    } else {
                        logInfo("Produced event", eventId, orderId, TOPIC, metadata.partition(), metadata.offset());
                    }
                });
                
                produced++;
                producedThisSecond++;
                
                // Small delay to avoid overwhelming
                if (ratePerSec == null && i % 100 == 0) {
                    Thread.sleep(10);
                }
            }
            
            producer.flush();
            long duration = System.currentTimeMillis() - startTime;
            double throughput = (produced * 1000.0) / duration;
            
            System.out.println("========================================");
            System.out.println("Producer Summary:");
            System.out.println("  Produced: " + produced);
            System.out.println("  Duration: " + duration + " ms");
            System.out.println("  Throughput: " + String.format("%.2f", throughput) + " events/sec");
            System.out.println("========================================");
            
        } catch (Exception e) {
            logError("Fatal error", null, null, e);
            System.exit(1);
        } finally {
            producer.close();
        }
    }
    
    private static void logInfo(String message, String eventId, String orderId, String topic, int partition, long offset) {
        ObjectNode log = mapper.createObjectNode();
        log.put("timestamp", Instant.now().toString());
        log.put("service", "producer_order");
        log.put("level", "INFO");
        log.put("message", message);
        log.put("event_id", eventId);
        log.put("order_id", orderId);
        log.put("topic", topic);
        log.put("partition", partition);
        log.put("offset", offset);
        System.out.println(log.toString());
    }
    
    private static void logError(String message, String eventId, String orderId, Exception e) {
        ObjectNode log = mapper.createObjectNode();
        log.put("timestamp", Instant.now().toString());
        log.put("service", "producer_order");
        log.put("level", "ERROR");
        log.put("message", message);
        if (eventId != null) log.put("event_id", eventId);
        if (orderId != null) log.put("order_id", orderId);
        log.put("error", e.getMessage());
        System.err.println(log.toString());
    }
}
