package com.ludoarena.security;

import com.ludoarena.model.Enums.Role;
import java.security.Principal;

/** Authenticated principal. getName() returns the user id so STOMP user destinations route per user. */
public record AuthUser(String id, String email, String name, Role role) implements Principal {
    @Override
    public String getName() {
        return id;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN || role == Role.SUPER_ADMIN;
    }
}
