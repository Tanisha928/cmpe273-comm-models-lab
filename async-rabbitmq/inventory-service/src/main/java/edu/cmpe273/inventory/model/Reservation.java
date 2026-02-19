package edu.cmpe273.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reservations")
public class Reservation {
  @Id
  private String orderId;

  private String itemId;
  private int quantity;
  private Instant createdAt;

  public Reservation() {}

  public Reservation(String orderId, String itemId, int quantity, Instant createdAt) {
    this.orderId = orderId;
    this.itemId = itemId;
    this.quantity = quantity;
    this.createdAt = createdAt;
  }

  public String getOrderId() { return orderId; }
  public String getItemId() { return itemId; }
  public int getQuantity() { return quantity; }
  public Instant getCreatedAt() { return createdAt; }
}
