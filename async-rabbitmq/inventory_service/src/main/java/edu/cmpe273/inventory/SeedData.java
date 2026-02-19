package edu.cmpe273.inventory;

import edu.cmpe273.inventory.model.Stock;
import edu.cmpe273.inventory.repo.StockRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SeedData implements CommandLineRunner {

  private final StockRepository stockRepository;

  public SeedData(StockRepository stockRepository) {
    this.stockRepository = stockRepository;
  }

  @Override
  public void run(String... args) {
    if (stockRepository.count() == 0) {
      stockRepository.save(new Stock("burger", 100000));
      stockRepository.save(new Stock("pizza", 100000));
      stockRepository.save(new Stock("salad", 100000));
      System.out.println("[seed] stock created");
    }
  }
}
