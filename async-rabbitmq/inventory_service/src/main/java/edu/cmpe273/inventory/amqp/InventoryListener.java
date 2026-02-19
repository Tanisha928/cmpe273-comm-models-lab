package edu.cmpe273.inventory.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cmpe273.inventory.config.RabbitConfig;
import edu.cmpe273.inventory.service.ReservationService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class InventoryListener {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ReservationService reservationService;
  private final RabbitTemplate rabbitTemplate;

  public InventoryListener(ReservationService reservationService,
                           RabbitTemplate rabbitTemplate) {
    this.reservationService = reservationService;
    this.rabbitTemplate = rabbitTemplate;
  }

  @RabbitListener(queues = RabbitConfig.Q_ORDER_PLACED)
  public void onOrderPlaced(Message msg) throws Exception {

    Map<String, Object> event;
    try {
      event = mapper.readValue(msg.getBody(), Map.class);
    } catch (Exception e) {
      System.out.println("[inventory] POISON message -> DLQ. body=" + new String(msg.getBody()));
      throw new IllegalArgumentException("poison_json", e);
    }

    if (!"OrderPlaced".equals(event.get("type"))) {
      return;
    }

    String orderId = (String) event.get("order_id");
    String itemId  = (String) event.get("item_id");
    int qty        = ((Number) event.get("quantity")).intValue();
    String userId  = (String) event.getOrDefault("user_id", "unknown");

    var res = reservationService.reserve(orderId, itemId, qty);

    Map<String, Object> out = new HashMap<>();
    out.put("type", res.ok() ? "InventoryReserved" : "InventoryFailed");
    out.put("order_id", orderId);
    out.put("item_id", itemId);
    out.put("quantity", qty);
    out.put("user_id", userId);
    out.put("ts", Instant.now().toString());
    out.put("reason", res.reason());

    String outType = (String) out.get("type");

    byte[] body = mapper.writeValueAsBytes(out);

    MessageProperties props = new MessageProperties();
    props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    props.setMessageId(orderId);

    Message outMsg = new Message(body, props);

    rabbitTemplate.send(RabbitConfig.EX_INVENTORY, outType, outMsg);

    System.out.printf("[inventory] order=%s type=%s reason=%s%n",
            orderId, outType, res.reason());
  }
}
