package com.example.gateway.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds {@code Cache-Control: no-store} and {@code X-Content-Type-Options: nosniff} to
 * every management response ({@code /api/**}) per spec security requirement #10.
 * The gateway ({@code /gw/**}) intentionally gets neither header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ManagementHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
        }
        filterChain.doFilter(request, response);
    }
}
