package com.p2pfileshare.tracker.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Role role;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected User() {}
    public User(String email, String passwordHash, Role role) { this.email = email; this.passwordHash = passwordHash; this.role = role; this.createdAt = Instant.now(); }
    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
    @Override public boolean equals(Object o) { return this == o || o instanceof User other && id != null && id.equals(other.id); }
    @Override public int hashCode() { return Objects.hashCode(id); }
}
