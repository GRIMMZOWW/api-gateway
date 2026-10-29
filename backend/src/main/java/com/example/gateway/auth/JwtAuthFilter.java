package com.example.gateway.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the {@code Authorization: Bearer <jwt>} header on every request. If the token
 * is valid, the user id is stored as the authentication principal so {@code SecurityUtils}
 * can read it later. Invalid or missing tokens leave the context empty; Spring Security's
 * {@code AuthenticationEntryPoint} then returns {@code 401}.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            JwtService.ParsedToken parsed = jwtService.parse(token);
            if (parsed != null) {
                var auth = new UsernamePasswordAuthenticationToken(
                    parsed.userId(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + parsed.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
