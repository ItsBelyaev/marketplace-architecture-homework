package ru.itsbelyaev.marketplace.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.itsbelyaev.marketplace.domain.UserRow;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRow> findByUsername(String username) {
        return jdbc.query("SELECT id, username, password_hash, role::text FROM users WHERE username = ?",
                (rs, rowNum) -> new UserRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role")), username).stream().findFirst();
    }

    public Optional<UserRow> findById(UUID id) {
        return jdbc.query("SELECT id, username, password_hash, role::text FROM users WHERE id = ?",
                (rs, rowNum) -> new UserRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role")), id).stream().findFirst();
    }

    public UserRow insert(String username, String passwordHash, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, username, password_hash, role) VALUES (?, ?, ?, CAST(? AS user_role))",
                id, username, passwordHash, role);
        return new UserRow(id, username, passwordHash, role);
    }
}
