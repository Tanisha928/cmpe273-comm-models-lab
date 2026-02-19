package edu.cmpe273.inventory.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {

  // Exchanges
  public static final String EX_ORDERS = "orders";
  public static final String EX_ORDERS_DLX = "orders.dlx";
  public static final String EX_INVENTORY = "inventory";

  // Queues
  public static final String Q_ORDER_PLACED = "q.order_placed";
  public static final String Q_ORDER_PLACED_DLQ = "q.order_placed.dlq";

  // Routing keys
  public static final String RK_ORDER_PLACED = "OrderPlaced";
  public static final String RK_ORDER_PLACED_DLQ = "OrderPlaced.dlq";

  public static final String RK_INV_RESERVED = "InventoryReserved";
  public static final String RK_INV_FAILED = "InventoryFailed";

  @Bean
  public TopicExchange ordersExchange() {
    return new TopicExchange(EX_ORDERS, true, false);
  }

  @Bean
  public TopicExchange ordersDlxExchange() {
    return new TopicExchange(EX_ORDERS_DLX, true, false);
  }

  @Bean
  public TopicExchange inventoryExchange() {
    return new TopicExchange(EX_INVENTORY, true, false);
  }

  @Bean
  public Queue orderPlacedQueue() {
    Map<String, Object> args = new HashMap<>();
    args.put("x-dead-letter-exchange", EX_ORDERS_DLX);
    args.put("x-dead-letter-routing-key", RK_ORDER_PLACED_DLQ);
    return new Queue(Q_ORDER_PLACED, true, false, false, args);
  }

  @Bean
  public Queue orderPlacedDlq() {
    return new Queue(Q_ORDER_PLACED_DLQ, true);
  }

  @Bean
  public Binding bindOrderPlaced(Queue orderPlacedQueue, TopicExchange ordersExchange) {
    return BindingBuilder.bind(orderPlacedQueue).to(ordersExchange).with(RK_ORDER_PLACED);
  }

  @Bean
  public Binding bindOrderPlacedDlq(Queue orderPlacedDlq, TopicExchange ordersDlxExchange) {
    return BindingBuilder.bind(orderPlacedDlq).to(ordersDlxExchange).with(RK_ORDER_PLACED_DLQ);
  }
}
