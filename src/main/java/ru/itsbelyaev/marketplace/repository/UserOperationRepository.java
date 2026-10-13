package ru.itsbelyaev.marketplace.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserOperationRepository {
    private final JdbcTemplate jdbc;

    public UserOperationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<OffsetDateTime> last(UUID userId, String operationType) {
        return jdbc.query("""
                SELECT created_at FROM user_operations
                WHERE user_id = ? AND operation_type = CAST(? AS operation_type)
                ORDER BY created_at DESC LIMIT 1
                """, (rs, rowNum) -> rs.getObject("created_at", OffsetDateTime.class), userId, operationType)
                .stream().findFirst();
    }

    public void insert(UUID userId, String operationType) {
        jdbc.update("""
                INSERT INTO user_operations(id, user_id, operation_type)
                VALUES (?, ?, CAST(? AS operation_type))
                """, UUID.randomUUID(), userId, operationType);
    }
}
