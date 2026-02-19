package edu.cmpe273.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cmpe273.order.config.RabbitConfig;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class OrderPublisher {

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper mapper = new ObjectMapper();

  public OrderPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public void publishOrderPlaced(String orderId, String userId, String itemId, int qty) throws Exception {
    Map<String, Object> ev = new HashMap<>();
    ev.put("type", "OrderPlaced");
    ev.put("order_id", orderId);
    ev.put("user_id", userId);
    ev.put("item_id", itemId);
    ev.put("quantity", qty);
    ev.put("ts", Instant.now().toString());

    byte[] body = mapper.writeValueAsBytes(ev);

    MessageProperties props = new MessageProperties();
    props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    props.setMessageId(orderId);

    Message msg = new Message(body, props);
    rabbitTemplate.send(RabbitConfig.EX_ORDERS, RabbitConfig.RK_ORDER_PLACED, msg);
  }
}
