package com.example.gateway.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity for the {@code users} table. Hibernate {@code ddl-auto=validate} checks
 * this against the Flyway schema on startup.
 *
 * <p>Per spec D0.8, every {@link Instant} column uses {@code columnDefinition="DATETIME(3)"}
 * so validation matches the Flyway {@code DATETIME(3)} columns.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Column(name = "created_at", columnDefinition = "DATETIME(3)", nullable = false)
    private Instant createdAt;

    protected User() {
        // For JPA.
    }

    public User(Long id, String email, String passwordHash, String displayName, UserRole role, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public UserRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }

    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setRole(UserRole role) { this.role = role; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
