package com.cmpe273.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AnalyticsConsumer {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String ORDER_TOPIC = "order_events";
    private static final String INVENTORY_TOPIC = "inventory_events";
    private static final String CONSUMER_GROUP = "analytics";
    private static final String METRICS_FILE = "/app/metrics_report.txt";
    
    // Event-time based metrics: minute bucket -> count
    private final Map<String, Integer> ordersPerMinute = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedOrdersPerMinute = new ConcurrentHashMap<>();
    private final Set<String> processedOrderEvents = ConcurrentHashMap.newKeySet();
    private final Set<String> processedInventoryEvents = ConcurrentHashMap.newKeySet();
    
    public void run() {
        String bootstrapServers = System.getenv().getOrDefault("BOOTSTRAP_SERVERS", "localhost:9092");
        String consumerGroup = System.getenv().getOrDefault("CONSUMER_GROUP", CONSUMER_GROUP);
        
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(ORDER_TOPIC, INVENTORY_TOPIC));
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logInfo("Shutting down analytics consumer...", null);
            writeMetricsReport();
            consumer.close();
        }));
        
        logInfo("Analytics Consumer started. Group: " + consumerGroup, null);
        
        long lastReportTime = System.currentTimeMillis();
        long reportInterval = 10000; // Report every 10 seconds
        
        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(java.time.Duration.ofMillis(100));
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        if (ORDER_TOPIC.equals(record.topic())) {
                            processOrderEvent(record);
                        } else if (INVENTORY_TOPIC.equals(record.topic())) {
                            processInventoryEvent(record);
                        }
                    } catch (Exception e) {
                        logError("Error processing record", record.topic(), record.key(), e);
                    }
                }
                
                consumer.commitSync();
                
                // Write report periodically
                long now = System.currentTimeMillis();
                if (now - lastReportTime >= reportInterval) {
                    writeMetricsReport();
                    lastReportTime = now;
                }
            }
        } catch (Exception e) {
            logError("Fatal error in consumer", null, null, e);
        } finally {
            writeMetricsReport();
            consumer.close();
        }
    }
    
    private void processOrderEvent(ConsumerRecord<String, String> record) throws Exception {
        JsonNode orderEvent = mapper.readTree(record.value());
        String eventId = orderEvent.get("event_id").asText();
        
        // Deduplicate based on event_id
        if (processedOrderEvents.contains(eventId)) {
            return;
        }
        processedOrderEvents.add(eventId);
        
        String createdAt = orderEvent.get("created_at").asText();
        
        // Bucket by minute based on event time (created_at)
        String minuteBucket = getMinuteBucket(createdAt);
        ordersPerMinute.put(minuteBucket, ordersPerMinute.getOrDefault(minuteBucket, 0) + 1);
        
        logInfo("Processed order event", ORDER_TOPIC, record.key(), record.partition(), record.offset());
    }
    
    private void processInventoryEvent(ConsumerRecord<String, String> record) throws Exception {
        JsonNode inventoryEvent = mapper.readTree(record.value());
        String eventId = inventoryEvent.get("event_id").asText();
        
        // Deduplicate based on event_id
        if (processedInventoryEvents.contains(eventId)) {
            return;
        }
        processedInventoryEvents.add(eventId);
        String type = inventoryEvent.get("type").asText();
        String createdAt = inventoryEvent.get("created_at").asText();
        
        // Use created_at from original order event for bucketing
        String minuteBucket = getMinuteBucket(createdAt);
        
        if ("InventoryFailed".equals(type)) {
            failedOrdersPerMinute.put(minuteBucket, failedOrdersPerMinute.getOrDefault(minuteBucket, 0) + 1);
        }
        
        logInfo("Processed inventory event", INVENTORY_TOPIC, record.key(), record.partition(), record.offset());
    }
    
    private String getMinuteBucket(String iso8601Timestamp) {
        Instant instant = Instant.parse(iso8601Timestamp);
        // Round down to minute
        Instant minuteStart = instant.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        return minuteStart.toString();
    }
    
    private void writeMetricsReport() {
        try (FileWriter writer = new FileWriter(METRICS_FILE)) {
            writer.write("========================================\n");
            writer.write("KAFKA STREAMING ANALYTICS REPORT\n");
            writer.write("Generated: " + Instant.now().toString() + "\n");
            writer.write("========================================\n\n");
            
            // Calculate total metrics
            int totalOrders = ordersPerMinute.values().stream().mapToInt(Integer::intValue).sum();
            int totalFailed = failedOrdersPerMinute.values().stream().mapToInt(Integer::intValue).sum();
            double failureRate = totalOrders > 0 ? (totalFailed * 100.0 / totalOrders) : 0.0;
            
            writer.write("OVERALL METRICS:\n");
            writer.write("  Total Orders: " + totalOrders + "\n");
            writer.write("  Total Failed: " + totalFailed + "\n");
            writer.write("  Failure Rate: " + String.format("%.2f", failureRate) + "%\n\n");
            
            writer.write("ORDERS PER MINUTE (Event Time):\n");
            writer.write("  Minute Bucket                    | Orders | Failed | Failure Rate\n");
            writer.write("  ----------------------------------|--------|--------|-------------\n");
            
            // Get all minute buckets (union of both maps)
            Set<String> allBuckets = new TreeSet<>();
            allBuckets.addAll(ordersPerMinute.keySet());
            allBuckets.addAll(failedOrdersPerMinute.keySet());
            
            for (String bucket : allBuckets) {
                int orders = ordersPerMinute.getOrDefault(bucket, 0);
                int failed = failedOrdersPerMinute.getOrDefault(bucket, 0);
                double bucketFailureRate = orders > 0 ? (failed * 100.0 / orders) : 0.0;
                
                writer.write(String.format("  %-35s | %6d | %6d | %10.2f%%\n", 
                    bucket, orders, failed, bucketFailureRate));
            }
            
            writer.write("\n========================================\n");
            writer.flush();
            
            logInfo("Metrics report written", null);
        } catch (IOException e) {
            logError("Failed to write metrics report", null, null, e);
        }
    }
    
    private static void logInfo(String message, String topic, String orderId, int partition, long offset) {
        ObjectNode log = mapper.createObjectNode();
        log.put("timestamp", Instant.now().toString());
        log.put("service", "analytics_consumer");
        log.put("level", "INFO");
        log.put("message", message);
        log.put("topic", topic);
        if (orderId != null) log.put("order_id", orderId);
        log.put("partition", partition);
        log.put("offset", offset);
        System.out.println(log.toString());
    }
    
    private static void logInfo(String message, String orderId) {
        ObjectNode log = mapper.createObjectNode();
        log.put("timestamp", Instant.now().toString());
        log.put("service", "analytics_consumer");
        log.put("level", "INFO");
        log.put("message", message);
        if (orderId != null) log.put("order_id", orderId);
        System.out.println(log.toString());
    }
    
    private static void logError(String message, String topic, String orderId, Exception e) {
        ObjectNode log = mapper.createObjectNode();
        log.put("timestamp", Instant.now().toString());
        log.put("service", "analytics_consumer");
        log.put("level", "ERROR");
        log.put("message", message);
        if (topic != null) log.put("topic", topic);
        if (orderId != null) log.put("order_id", orderId);
        log.put("error", e.getMessage());
        System.err.println(log.toString());
    }
    
    public static void main(String[] args) {
        new AnalyticsConsumer().run();
    }
}
