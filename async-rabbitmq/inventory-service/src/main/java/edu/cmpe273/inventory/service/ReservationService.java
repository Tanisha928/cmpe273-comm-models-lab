package edu.cmpe273.inventory.service;

import edu.cmpe273.inventory.model.Reservation;
import edu.cmpe273.inventory.model.Stock;
import edu.cmpe273.inventory.repo.ReservationRepository;
import edu.cmpe273.inventory.repo.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ReservationService {

  private final ReservationRepository reservationRepository;
  private final StockRepository stockRepository;

  public ReservationService(ReservationRepository reservationRepository, StockRepository stockRepository) {
    this.reservationRepository = reservationRepository;
    this.stockRepository = stockRepository;
  }

  public record Result(boolean ok, String reason) {}

  @Transactional
  public Result reserve(String orderId, String itemId, int qty) {
    // 1) idempotency: if we already processed this order, DO NOT reserve again
    if (reservationRepository.existsById(orderId)) {
      return new Result(true, "idempotent");
    }

    // 2) check stock
    Stock s = stockRepository.findById(itemId).orElse(null);
    if (s == null) return new Result(false, "unknown_item");
    if (s.getQty() < qty) return new Result(false, "insufficient");

    // 3) reserve
    s.setQty(s.getQty() - qty);
    stockRepository.save(s);

    reservationRepository.save(new Reservation(orderId, itemId, qty, Instant.now()));
    return new Result(true, "reserved");
  }
}
