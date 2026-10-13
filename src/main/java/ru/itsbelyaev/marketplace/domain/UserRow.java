package ru.itsbelyaev.marketplace.domain;

import java.util.UUID;

public record UserRow(UUID id, String username, String passwordHash, String role) {
}
