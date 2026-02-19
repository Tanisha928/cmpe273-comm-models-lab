package edu.cmpe273.order.api;

import edu.cmpe273.order.model.OrderEntity;
import edu.cmpe273.order.repo.OrderRepository;
import edu.cmpe273.order.service.OrderPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
public class OrderController {

  private final OrderRepository orderRepository;
  private final OrderPublisher publisher;

  public OrderController(OrderRepository orderRepository, OrderPublisher publisher) {
    this.orderRepository = orderRepository;
    this.publisher = publisher;
  }

  @PostMapping("/order")
  public ResponseEntity<?> create(@RequestBody OrderRequest req) throws Exception {
    String orderId = (req.order_id != null && !req.order_id.isBlank())
        ? req.order_id
        : UUID.randomUUID().toString();

    // local store (lab requirement)
    orderRepository.save(new OrderEntity(
        orderId, req.user_id, req.item_id, req.quantity, "ACCEPTED", Instant.now()
    ));

    // publish async event
    publisher.publishOrderPlaced(orderId, req.user_id, req.item_id, req.quantity);

    return ResponseEntity.accepted().body(Map.of(
        "accepted", true,
        "order_id", orderId
    ));
  }
}
