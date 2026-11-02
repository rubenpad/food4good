package com.food4good.storage.domain.ports.out;

import com.food4good.storage.domain.model.ReservationLine;
import com.food4good.storage.domain.ports.in.IngredientRequest;
import java.util.List;
import java.util.UUID;

public interface ReservationRepositoryPort {

    void insertLines(List<IngredientRequest> requests);

    List<ReservationLine> findLines(UUID orderId);

    void save(ReservationLine line);
}
