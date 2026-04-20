package com.phoneserver.controlplane.security;

import java.io.IOException;

import com.phoneserver.controlplane.model.Device;
import com.phoneserver.controlplane.repository.DeviceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DeviceTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String DEVICE_TOKEN_HEADER = "X-Device-Token";

    private final DeviceRepository deviceRepository;

    public DeviceTokenAuthenticationFilter(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return !request.getServletPath().startsWith("/api/v1/agent/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String deviceToken = request.getHeader(DEVICE_TOKEN_HEADER);
        if (deviceToken == null || deviceToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Device device = deviceRepository.findByDeviceToken(deviceToken.trim()).orElse(null);
        if (device != null) {
            DeviceAgentPrincipal principal = DeviceAgentPrincipal.fromEntity(device);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
