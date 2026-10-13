package ru.itsbelyaev.marketplace.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import ru.itsbelyaev.marketplace.domain.OrderItemRow;
import ru.itsbelyaev.marketplace.domain.OrderRow;

@Repository
public class OrderRepository {
    private static final String SELECT_ORDER = """
            SELECT o.*, p.code AS promo_code
            FROM orders o
            LEFT JOIN promo_codes p ON p.id = o.promo_code_id
            """;
    private static final RowMapper<OrderRow> ORDER_MAPPER = OrderRepository::mapOrder;
    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasActive(UUID userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM orders
                WHERE user_id = ? AND status IN ('CREATED', 'PAYMENT_PENDING')
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    public OrderRow insert(UUID userId, UUID promoCodeId, java.math.BigDecimal total,
                           java.math.BigDecimal discount) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO orders(id, user_id, status, promo_code_id, total_amount, discount_amount)
                VALUES (?, ?, 'CREATED', ?, ?, ?)
                """, id, userId, promoCodeId, total, discount);
        return findById(id).orElseThrow();
    }

    public Optional<OrderRow> findById(UUID id) {
        return jdbc.query(SELECT_ORDER + " WHERE o.id = ?", ORDER_MAPPER, id).stream().findFirst();
    }

    public Optional<OrderRow> findForUpdate(UUID id) {
        return jdbc.query(SELECT_ORDER + " WHERE o.id = ? FOR UPDATE OF o", ORDER_MAPPER, id)
                .stream().findFirst();
    }

    public List<OrderItemRow> findItems(UUID orderId) {
        return jdbc.query("SELECT * FROM order_items WHERE order_id = ? ORDER BY id",
                (rs, rowNum) -> new OrderItemRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        rs.getObject("product_id", UUID.class),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("price_at_order")), orderId);
    }

    public void insertItems(List<OrderItemRow> items) {
        jdbc.batchUpdate("""
                INSERT INTO order_items(id, order_id, product_id, quantity, price_at_order)
                VALUES (?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(java.sql.PreparedStatement ps, int i) throws SQLException {
                OrderItemRow item = items.get(i);
                ps.setObject(1, item.id());
                ps.setObject(2, item.orderId());
                ps.setObject(3, item.productId());
                ps.setInt(4, item.quantity());
                ps.setBigDecimal(5, item.priceAtOrder());
            }

            @Override
            public int getBatchSize() {
                return items.size();
            }
        });
    }

    public void deleteItems(UUID orderId) {
        jdbc.update("DELETE FROM order_items WHERE order_id = ?", orderId);
    }

    public void update(UUID id, UUID promoCodeId, java.math.BigDecimal total, java.math.BigDecimal discount) {
        jdbc.update("""
                UPDATE orders SET promo_code_id = ?, total_amount = ?, discount_amount = ? WHERE id = ?
                """, promoCodeId, total, discount, id);
    }

    public void cancel(UUID id) {
        jdbc.update("UPDATE orders SET status = 'CANCELED' WHERE id = ?", id);
    }

    private static OrderRow mapOrder(ResultSet rs, int rowNum) throws SQLException {
        return new OrderRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("status"),
                rs.getObject("promo_code_id", UUID.class),
                rs.getString("promo_code"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("discount_amount"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
