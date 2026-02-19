package edu.cmpe273.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock")
public class Stock {
  @Id
  private String itemId;

  private long qty;

  public Stock() {}

  public Stock(String itemId, long qty) {
    this.itemId = itemId;
    this.qty = qty;
  }

  public String getItemId() { return itemId; }
  public long getQty() { return qty; }
  public void setQty(long qty) { this.qty = qty; }
}
