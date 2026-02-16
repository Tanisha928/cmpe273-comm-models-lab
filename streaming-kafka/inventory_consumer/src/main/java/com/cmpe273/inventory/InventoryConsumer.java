package com.cmpe273.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryConsumer {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String ORDER_TOPIC = "order_events";
    private static final String INVENTORY_TOPIC = "inventory_events";
    private static final String CONSUMER_GROUP = "inventory";
    
    private final Map<String, Integer> stock = new ConcurrentHashMap<>();
    private final Set<String> processedOrders = ConcurrentHashMap.newKeySet();
    private final Path processedOrdersFile;
    
    public InventoryConsumer() {
        // Initialize inventory
        stock.put("burrito", 5000);
        stock.put("pizza", 5000);
        
        // Load processed orders from file for idempotency
        processedOrdersFile = Paths.get("/app/processed_orders/orders.txt");
        try {
            Files.createDirectories(processedOrdersFile.getParent());
            if (Files.exists(processedOrdersFile)) {
                Files.lines(processedOrdersFile).forEach(processedOrders::add);
                logInfo("Loaded " + processedOrders.size() + " processed orders from file");
            }
        } catch (IOException e) {
            logError("Failed to load processed orders", null, e);
        }
    }
    
    public void run() {
        String bootstrapServers = System.getenv().getOrDefault("BOOTSTRAP_SERVERS", "localhost:9092");
        String consumerGroup = System.getenv().getOrDefault("CONSUMER_GROUP", CONSUMER_GROUP);
        long throttleMs = Long.parseLong(System.getenv().getOrDefault("THROTTLE_MS_PER_MSG", "0"));
        
        // Consumer properties
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        
        // Producer properties
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps);
        
        consumer.subscribe(Collections.singletonList(ORDER_TOPIC));
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logInfo("Shutting down consumer...", null);
            consumer.close();
            producer.close();
        }));
        
        logInfo("Inventory Consumer started. Group: " + consumerGroup, null);
        
        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(java.time.Duration.ofMillis(100));
                
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        processOrder(record, producer, throttleMs);
                    } catch (Exception e) {
                        logError("Error processing record", record.key(), e);
                    }
                }
                
                consumer.commitSync();
            }
        } catch (Exception e) {
            logError("Fatal error in consumer", null, e);
        } finally {
            consumer.close();
            producer.close();
        }
    }
    
    private void processOrder(ConsumerRecord<String, String> record, KafkaProducer<String, String> producer, long throttleMs) throws Exception {
        String orderId = record.key();
        
        // Idempotency check
        if (processedOrders.contains(orderId)) {
            logInfo("Duplicate order detected, skipping: " + orderId, orderId);
            return;
        }
        
        // Throttle if configured
        if (throttleMs > 0) {
            Thread.sleep(throttleMs);
        }
        
        JsonNode orderEvent = mapper.readTree(record.value());
        String eventId = orderEvent.get("event_id").asText();
        String itemId = orderEvent.get("item_id").asText();
        int qty = orderEvent.get("qty").asInt();
        String createdAt = orderEvent.get("created_at").asText();
        
        Instant processedAt = Instant.now();
        String inventoryEventId = UUID.randomUUID().toString();
        
        ObjectNode inventoryEvent = mapper.createObjectNode();
        inventoryEvent.put("event_id", inventoryEventId);
        inventoryEvent.put("order_id", orderId);
        inventoryEvent.put("item_id", itemId);
        inventoryEvent.put("qty", qty);
        inventoryEvent.put("created_at", createdAt);
        inventoryEvent.put("processed_at", processedAt.toString());
        
        // Check inventory
        Integer currentStock = stock.get(itemId);
        if (currentStock == null || currentStock < qty) {
            inventoryEvent.put("type", "InventoryFailed");
            inventoryEvent.put("reason", currentStock == null ? "ITEM_NOT_FOUND" : "OUT_OF_STOCK");
            
            logInfo("Inventory reservation failed", orderId, itemId, qty, "OUT_OF_STOCK");
        } else {
            stock.put(itemId, currentStock - qty);
            inventoryEvent.put("type", "InventoryReserved");
            inventoryEvent.put("reason", "");
            
            processedOrders.add(orderId);
            saveProcessedOrder(orderId);
            
            logInfo("Inventory reserved", orderId, itemId, qty, "RESERVED");
        }
        
        String eventJson = mapper.writeValueAsString(inventoryEvent);
        ProducerRecord<String, String> inventoryRecord = new ProducerRecord<>(INVENTORY_TOPIC, orderId, eventJson);
        
        producer.send(inventoryRecord, (metadata, exception) -> {
            if (exception != null) {
                logError("Failed to produce inventory event", orderId, exception);
            } else {
                logInfo("Produced inventory event", orderId, INVENTORY_TOPIC, metadata.partition(), metadata.offset());
            }
        });
    }
    
    private void saveProcessedOrder(String orderId) {
        try {
            Files.write(processedOrdersFile, (orderId + "\n").getBytes(), 
                       java.nio.file.StandardOpenOption.CREATE, 
                       java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            logError("Failed to save processed order", orderId, e);
        }
    }
    
    private static void logInfo(String message, String orderId, String itemId, int qty, String status) {
        ObjectNode log = mapper.createObjectNode();
        log.put("timestamp", Instant.now().toString());
        log.put("service", "inventory_consumer");
        log.put("level", "INFO");
        log.put("message", message);
        if (orderId != null) log.put("order_id", orderId);
        if (itemId != null) log.put("item_id", itemId);
        log.put("qty", qty);
        log.put("status", status);
        System.out.println(log.toString());
    }
    
    private static void logInfo(String message) {
        ObjectNode log = mapper.createObjectNode();
        log.put("timestamp", Instant.now().toString());
        log.put("service", "inventory_consumer");
        log.put("level", "INFO");
        log.put("message", message);
        System.out.println(log.toString());
    }

    private static void logInfo(String message, String orderId) {
        ObjectNode log = mapper.createObjectNode();
        log.put("timestamp", Instant.now().toString());
        log.put("service", "inventory_consumer");
        log.put("level", "INFO");
        log.put("message", message);
        if (orderId != null) log.put("order_id", orderId);
        System.out.println(log.toString());
    }
    
    private static void logInfo(String message, String orderId, String topic, int partition, long offset) {
        ObjectNode log = mapper.createObjectNode();
        log.put("timestamp", Instant.now().toString());
        log.put("service", "inventory_consumer");
        log.put("level", "INFO");
        log.put("message", message);
        if (orderId != null) log.put("order_id", orderId);
        log.put("topic", topic);
        log.put("partition", partition);
        log.put("offset", offset);
        System.out.println(log.toString());
    }
    
    private static void logError(String message, String orderId, Exception e) {
        ObjectNode log = mapper.createObjectNode();
        log.put("timestamp", Instant.now().toString());
        log.put("service", "inventory_consumer");
        log.put("level", "ERROR");
        log.put("message", message);
        if (orderId != null) log.put("order_id", orderId);
        log.put("error", e.getMessage());
        System.err.println(log.toString());
    }
    
    public static void main(String[] args) {
        new InventoryConsumer().run();
    }
}
