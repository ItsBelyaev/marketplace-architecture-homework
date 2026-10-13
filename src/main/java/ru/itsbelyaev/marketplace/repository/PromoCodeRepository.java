package ru.itsbelyaev.marketplace.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import ru.itsbelyaev.marketplace.domain.PromoCodeRow;
import ru.itsbelyaev.marketplace.generated.model.PromoCodeCreate;

@Repository
public class PromoCodeRepository {
    private static final RowMapper<PromoCodeRow> MAPPER = PromoCodeRepository::map;
    private final JdbcTemplate jdbc;

    public PromoCodeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PromoCodeRow insert(PromoCodeCreate promo) {
        UUID id = UUID.randomUUID();
        return jdbc.queryForObject("""
                INSERT INTO promo_codes(id, code, discount_type, discount_value, min_order_amount,
                    max_uses, valid_from, valid_until, active)
                VALUES (?, ?, CAST(? AS discount_type), ?, ?, ?, ?, ?, ?)
                RETURNING *
                """, MAPPER, id, promo.getCode(), promo.getDiscountType().getValue(), promo.getDiscountValue(),
                promo.getMinOrderAmount(), promo.getMaxUses(), promo.getValidFrom(), promo.getValidUntil(),
                promo.getActive());
    }

    public Optional<PromoCodeRow> findByCodeForUpdate(String code) {
        return jdbc.query("SELECT * FROM promo_codes WHERE code = ? FOR UPDATE", MAPPER, code)
                .stream().findFirst();
    }

    public Optional<PromoCodeRow> findByIdForUpdate(UUID id) {
        return jdbc.query("SELECT * FROM promo_codes WHERE id = ? FOR UPDATE", MAPPER, id)
                .stream().findFirst();
    }

    public void changeUsage(UUID id, int delta) {
        jdbc.update("UPDATE promo_codes SET current_uses = current_uses + ? WHERE id = ?", delta, id);
    }

    private static PromoCodeRow map(ResultSet rs, int rowNum) throws SQLException {
        return new PromoCodeRow(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("discount_type"),
                rs.getBigDecimal("discount_value"),
                rs.getBigDecimal("min_order_amount"),
                rs.getInt("max_uses"),
                rs.getInt("current_uses"),
                rs.getObject("valid_from", OffsetDateTime.class),
                rs.getObject("valid_until", OffsetDateTime.class),
                rs.getBoolean("active"));
    }
}
