package com.example.my_app.positions;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StockPositionRepository extends MongoRepository<StockPosition, String> {

  Optional<StockPosition> findBySymbol(String symbol);

  List<StockPosition> findBySymbolIn(Collection<String> symbols);
}
