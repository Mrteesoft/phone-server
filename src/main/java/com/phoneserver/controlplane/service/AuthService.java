package com.phoneserver.controlplane.service;

import java.time.Clock;
import java.util.Locale;

import com.phoneserver.controlplane.dto.request.AuthLoginRequest;
import com.phoneserver.controlplane.dto.request.AuthRegisterRequest;
import com.phoneserver.controlplane.dto.response.AuthResponse;
import com.phoneserver.controlplane.exception.BadRequestException;
import com.phoneserver.controlplane.model.User;
import com.phoneserver.controlplane.repository.UserRepository;
import com.phoneserver.controlplane.security.AppUserPrincipal;
import com.phoneserver.controlplane.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(AuthRegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("An account with that email already exists.");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(AppUserPrincipal.fromEntity(savedUser));

        return AuthResponse.fromEntity(savedUser, token, clock.instant());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthLoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new BadRequestException("Invalid email or password."));

        String token = jwtService.generateToken(AppUserPrincipal.fromEntity(user));
        return AuthResponse.fromEntity(user, token, clock.instant());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

