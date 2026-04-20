package com.phoneserver.controlplane.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.phoneserver.controlplane.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AppUserPrincipal implements UserDetails {

    private final UUID userId;
    private final String email;
    private final String passwordHash;

    public AppUserPrincipal(UUID userId, String email, String passwordHash) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static AppUserPrincipal fromEntity(User user) {
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

