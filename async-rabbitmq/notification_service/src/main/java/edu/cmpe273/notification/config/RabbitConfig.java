package edu.cmpe273.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

  public static final String EX_INVENTORY = "inventory";
  public static final String Q_INV_RESERVED = "q.inventory_reserved";

  @Bean
  public TopicExchange inventoryExchange() {
    return new TopicExchange(EX_INVENTORY, true, false);
  }

  @Bean
  public Queue inventoryReservedQueue() {
    return new Queue(Q_INV_RESERVED, true);
  }

  @Bean
  public Binding bindInventoryReserved(Queue inventoryReservedQueue,
                                       TopicExchange inventoryExchange) {
    return BindingBuilder.bind(inventoryReservedQueue)
            .to(inventoryExchange)
            .with("InventoryReserved");
  }
}
