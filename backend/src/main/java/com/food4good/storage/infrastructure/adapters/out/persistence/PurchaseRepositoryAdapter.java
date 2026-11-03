package com.food4good.storage.infrastructure.adapters.out.persistence;

import com.food4good.shared.domain.model.Ingredient;
import com.food4good.storage.domain.model.Purchase;
import com.food4good.storage.domain.ports.out.PurchaseRepositoryPort;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class PurchaseRepositoryAdapter implements PurchaseRepositoryPort {

    private final JdbcClient jdbc;

    PurchaseRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(long marketJobId, int attempt, Ingredient ingredient, int quantity) {
        jdbc.sql("""
                        INSERT INTO purchase (market_job_id, attempt, ingredient, quantity)
                        VALUES (:marketJobId, :attempt, :ingredient, :quantity)
                        """)
                .param("marketJobId", marketJobId)
                .param("attempt", attempt)
                .param("ingredient", ingredient.id())
                .param("quantity", quantity)
                .update();
    }

    @Override
    public List<Purchase> find(Long before, int limit) {
        var sql = new StringBuilder("SELECT id, ingredient, quantity, purchased_at FROM purchase");
        if (before != null) {
            sql.append(" WHERE id < :before");
        }
        sql.append(" ORDER BY id DESC LIMIT :limit");
        var statement = jdbc.sql(sql.toString()).param("limit", limit);
        if (before != null) {
            statement = statement.param("before", before);
        }
        return statement
                .query((row, rowNumber) -> new Purchase(
                        row.getLong("id"),
                        Ingredient.fromId(row.getString("ingredient")),
                        row.getInt("quantity"),
                        row.getObject("purchased_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
