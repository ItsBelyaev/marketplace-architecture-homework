package ru.itsbelyaev.marketplace.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.itsbelyaev.marketplace.domain.ProductRow;
import ru.itsbelyaev.marketplace.generated.model.ProductCreate;
import ru.itsbelyaev.marketplace.generated.model.ProductUpdate;

@Repository
public class ProductRepository {
    private static final RowMapper<ProductRow> MAPPER = ProductRepository::map;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public ProductRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    public ProductRow insert(ProductCreate product, UUID sellerId) {
        UUID id = UUID.randomUUID();
        return jdbc.queryForObject("""
                INSERT INTO products(id, name, description, price, stock, category, status, seller_id)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS product_status), ?)
                RETURNING *
                """, MAPPER, id, product.getName(), product.getDescription(), product.getPrice(),
                product.getStock(), product.getCategory(), product.getStatus().getValue(), sellerId);
    }

    public Optional<ProductRow> findById(UUID id) {
        return jdbc.query("SELECT * FROM products WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<ProductRow> findForUpdate(UUID id) {
        return jdbc.query("SELECT * FROM products WHERE id = ? FOR UPDATE", MAPPER, id).stream().findFirst();
    }

    public List<ProductRow> findAllForUpdate(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return namedJdbc.query("SELECT * FROM products WHERE id IN (:ids) ORDER BY id FOR UPDATE",
                Map.of("ids", ids), MAPPER);
    }

    public ProductRow update(UUID id, ProductUpdate product) {
        return jdbc.queryForObject("""
                UPDATE products
                SET name = ?, description = ?, price = ?, stock = ?, category = ?, status = CAST(? AS product_status)
                WHERE id = ?
                RETURNING *
                """, MAPPER, product.getName(), product.getDescription(), product.getPrice(), product.getStock(),
                product.getCategory(), product.getStatus().getValue(), id);
    }

    public void archive(UUID id) {
        jdbc.update("UPDATE products SET status = 'ARCHIVED' WHERE id = ?", id);
    }

    public void adjustStock(UUID id, int delta) {
        jdbc.update("UPDATE products SET stock = stock + ? WHERE id = ?", delta, id);
    }

    public PageResult list(int page, int size, String status, String category) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new java.util.HashMap<>();
        if (status != null) {
            where.append(" AND status = CAST(:status AS product_status)");
            params.put("status", status);
        }
        if (category != null) {
            where.append(" AND category = :category");
            params.put("category", category);
        }
        long total = namedJdbc.queryForObject("SELECT COUNT(*) FROM products" + where, params, Long.class);
        params.put("limit", size);
        params.put("offset", (long) page * size);
        List<ProductRow> items = namedJdbc.query(
                "SELECT * FROM products" + where + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset",
                params, MAPPER);
        return new PageResult(items, total);
    }

    private static ProductRow map(ResultSet rs, int rowNum) throws SQLException {
        return new ProductRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                rs.getInt("stock"),
                rs.getString("category"),
                rs.getString("status"),
                rs.getObject("seller_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    public record PageResult(List<ProductRow> items, long total) {
    }
}
