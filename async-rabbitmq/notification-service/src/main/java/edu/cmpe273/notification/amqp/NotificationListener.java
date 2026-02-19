package edu.cmpe273.notification.amqp;


import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationListener {


  @RabbitListener(queues = "q.inventory_reserved")
public void onInventoryReserved(Map<String, Object> ev) {
  System.out.printf(
      "[notification] CONFIRM order=%s user=%s reason=%s%n",
      ev.get("order_id"),
      ev.get("user_id"),
      ev.get("reason")
  );
}


}
