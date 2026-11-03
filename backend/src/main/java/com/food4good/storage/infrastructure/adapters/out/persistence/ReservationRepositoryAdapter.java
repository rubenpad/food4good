package com.food4good.storage.infrastructure.adapters.out.persistence;

import com.food4good.shared.domain.model.Ingredient;
import com.food4good.storage.domain.model.ReservationLine;
import com.food4good.storage.domain.ports.in.IngredientRequest;
import com.food4good.storage.domain.ports.out.ReservationRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ReservationRepositoryAdapter implements ReservationRepositoryPort {

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;

    ReservationRepositoryAdapter(JdbcClient jdbc, JdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    @Override
    public void insertLines(List<IngredientRequest> requests) {
        batch.batchUpdate(
                "INSERT INTO reservation_line (order_id, ingredient, needed) VALUES (?, ?, ?)",
                requests.stream()
                        .flatMap(request -> request.quantities().entrySet().stream()
                                .map(entry -> new Object[] {request.orderId(), entry.getKey().id(), entry.getValue()}))
                        .toList());
    }

    @Override
    public List<ReservationLine> findLines(UUID orderId) {
        // Ordered so the same Order always reserves its Ingredients in the same sequence.
        return jdbc.sql("""
                        SELECT ingredient, needed, reserved, consumed
                        FROM reservation_line
                        WHERE order_id = :orderId
                        ORDER BY ingredient
                        """)
                .param("orderId", orderId)
                .query((row, rowNumber) -> new ReservationLine(
                        orderId,
                        Ingredient.fromId(row.getString("ingredient")),
                        row.getInt("needed"),
                        row.getInt("reserved"),
                        row.getBoolean("consumed")))
                .list();
    }

    @Override
    public void save(ReservationLine line) {
        jdbc.sql("""
                        UPDATE reservation_line
                        SET reserved = :reserved, consumed = :consumed
                        WHERE order_id = :orderId AND ingredient = :ingredient
                        """)
                .param("reserved", line.reserved())
                .param("consumed", line.consumed())
                .param("orderId", line.orderId())
                .param("ingredient", line.ingredient().id())
                .update();
    }
}
