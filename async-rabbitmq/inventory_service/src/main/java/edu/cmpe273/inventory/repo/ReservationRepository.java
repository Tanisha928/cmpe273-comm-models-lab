package edu.cmpe273.inventory.repo;

import edu.cmpe273.inventory.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, String> {}
