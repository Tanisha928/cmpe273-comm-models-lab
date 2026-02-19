package edu.cmpe273.order.api;

public class OrderRequest {
  public String order_id;   // allow client to pass it (useful for idempotency test)
  public String user_id;
  public String item_id;
  public int quantity;
}
