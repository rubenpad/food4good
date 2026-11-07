package com.food4good.storage.infrastructure.adapters.out.persistence;

import com.food4good.storage.domain.model.StuckOrder;
import com.food4good.storage.domain.ports.out.StuckOrderRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class StuckOrderRepositoryAdapter implements StuckOrderRepositoryPort {

    private final JdbcClient jdbc;

    StuckOrderRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StuckOrder> findStuck(int maxRepairs, int limit) {
        return jdbc.sql("""
                        SELECT o.id,
                               EXISTS (SELECT 1 FROM reservation_line l WHERE l.order_id = o.id) AS has_lines,
                               (SELECT count(*) FROM storage_command c
                                WHERE c.dedupe_key LIKE 'REPAIR:' || o.id || ':%') AS repairs
                        FROM orders o
                        WHERE o.status = 'WAITING_FOR_INGREDIENTS'
                          AND NOT EXISTS (
                              SELECT 1 FROM storage_command c
                              WHERE c.status = 'PENDING'
                                AND (c.payload ->> 'orderId' = o.id::text
                                  OR c.payload -> 'requests' @> jsonb_build_array(jsonb_build_object('orderId', o.id::text))))
                          AND NOT EXISTS (
                              SELECT 1 FROM market_job j
                              WHERE j.order_id = o.id AND j.status IN ('PENDING', 'IN_FLIGHT'))
                          AND (NOT EXISTS (SELECT 1 FROM reservation_line l WHERE l.order_id = o.id)
                            OR EXISTS (SELECT 1 FROM reservation_line l WHERE l.order_id = o.id AND l.reserved < l.needed))
                        ORDER BY repairs, o.accepted_at
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query((row, rowNumber) -> new StuckOrder(
                        row.getObject("id", UUID.class),
                        row.getBoolean("has_lines"),
                        Math.min(row.getInt("repairs"), maxRepairs)))
                .list();
    }
}
