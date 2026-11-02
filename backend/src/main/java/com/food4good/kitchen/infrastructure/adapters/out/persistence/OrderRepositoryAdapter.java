package com.food4good.kitchen.infrastructure.adapters.out.persistence;

import com.food4good.kitchen.domain.model.NewOrder;
import com.food4good.kitchen.domain.model.Order;
import com.food4good.kitchen.domain.model.OrderQuery;
import com.food4good.kitchen.domain.model.OrderStatus;
import com.food4good.kitchen.domain.ports.out.OrderRepositoryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final JdbcClient jdbc;
    private final JdbcTemplate batch;

    OrderRepositoryAdapter(JdbcClient jdbc, JdbcTemplate batch) {
        this.jdbc = jdbc;
        this.batch = batch;
    }

    @Override
    public void insertAll(List<NewOrder> orders) {
        batch.batchUpdate(
                "INSERT INTO orders (id, recipe_id, recipe_name, preparation_ms) VALUES (?, ?, ?, ?)",
                orders.stream()
                        .map(order -> new Object[] {
                            order.id(),
                            order.recipe().id(),
                            order.recipe().name(),
                            order.recipe().preparationTime().toMillis()
                        })
                        .toList());
    }

    @Override
    public List<Order> find(OrderQuery query) {
        var sql = new StringBuilder("""
                SELECT id, recipe_id, recipe_name, preparation_ms, status,
                       accepted_at, ready_at, preparing_at, delivered_at
                FROM orders
                WHERE true
                """);
        if (query.status() != null) {
            sql.append(" AND status = :status");
        }
        if (query.cursor() != null) {
            sql.append(" AND (accepted_at, id) < (:cursorAcceptedAt, :cursorId)");
        }
        sql.append(" ORDER BY accepted_at DESC, id DESC LIMIT :fetch");

        var statement = jdbc.sql(sql.toString()).param("fetch", query.limit() + 1);
        if (query.status() != null) {
            statement = statement.param("status", query.status().name());
        }
        if (query.cursor() != null) {
            statement = statement
                    .param("cursorAcceptedAt", OffsetDateTime.ofInstant(query.cursor().acceptedAt(), ZoneOffset.UTC))
                    .param("cursorId", query.cursor().id());
        }
        return statement.query((row, rowNumber) -> toOrder(row)).list();
    }

    @Override
    public void markReady(UUID orderId) {
        jdbc.sql("UPDATE orders SET status = 'READY', ready_at = now() WHERE id = :id AND status = 'WAITING_FOR_INGREDIENTS'")
                .param("id", orderId)
                .update();
    }

    private static Order toOrder(ResultSet row) throws SQLException {
        return new Order(
                row.getObject("id", UUID.class),
                row.getObject("recipe_id", UUID.class),
                row.getString("recipe_name"),
                Duration.ofMillis(row.getLong("preparation_ms")),
                OrderStatus.valueOf(row.getString("status")),
                instant(row, "accepted_at"),
                instant(row, "ready_at"),
                instant(row, "preparing_at"),
                instant(row, "delivered_at"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        var value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
