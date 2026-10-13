package ru.itsbelyaev.marketplace.security;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String username, String role) {
    public boolean hasRole(String expected) {
        return role.equals(expected);
    }
}
