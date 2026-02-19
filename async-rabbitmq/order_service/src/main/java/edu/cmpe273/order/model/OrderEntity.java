package edu.cmpe273.order.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "orders")
public class OrderEntity {

  @Id
  private String orderId;

  private String userId;
  private String itemId;
  private int quantity;

  private String status; // ACCEPTED
  private Instant createdAt;

  public OrderEntity() {}

  public OrderEntity(String orderId, String userId, String itemId, int quantity, String status, Instant createdAt) {
    this.orderId = orderId;
    this.userId = userId;
    this.itemId = itemId;
    this.quantity = quantity;
    this.status = status;
    this.createdAt = createdAt;
  }

  public String getOrderId() { return orderId; }
  public String getUserId() { return userId; }
  public String getItemId() { return itemId; }
  public int getQuantity() { return quantity; }
  public String getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
}
