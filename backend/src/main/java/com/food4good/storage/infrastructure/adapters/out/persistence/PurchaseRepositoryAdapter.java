package com.food4good.storage.infrastructure.adapters.out.persistence;

import com.food4good.shared.domain.model.Ingredient;
import com.food4good.storage.domain.model.Purchase;
import com.food4good.storage.domain.ports.out.PurchaseRepositoryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    public long insert(long marketJobId, int attempt, Ingredient ingredient, int quantity) {
        return jdbc.sql("""
                        INSERT INTO purchase (market_job_id, attempt, ingredient, quantity)
                        VALUES (:marketJobId, :attempt, :ingredient, :quantity)
                        RETURNING id
                        """)
                .param("marketJobId", marketJobId)
                .param("attempt", attempt)
                .param("ingredient", ingredient.id())
                .param("quantity", quantity)
                .query(Long.class)
                .single();
    }

    @Override
    public List<Purchase> findByIds(List<Long> ids) {
        return jdbc.sql("""
                        SELECT id, ingredient, quantity, purchased_at FROM purchase
                        WHERE id = ANY(:ids) ORDER BY id
                        """)
                .param("ids", ids.toArray(Long[]::new))
                .query(PurchaseRepositoryAdapter::toPurchase)
                .list();
    }

    private static Purchase toPurchase(ResultSet row, int rowNumber) throws SQLException {
        return new Purchase(
                row.getLong("id"),
                Ingredient.fromId(row.getString("ingredient")),
                row.getInt("quantity"),
                row.getObject("purchased_at", OffsetDateTime.class).toInstant());
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
                .query(PurchaseRepositoryAdapter::toPurchase)
                .list();
    }
}
