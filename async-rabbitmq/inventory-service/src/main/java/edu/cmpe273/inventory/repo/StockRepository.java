package edu.cmpe273.inventory.repo;

import edu.cmpe273.inventory.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, String> {}
