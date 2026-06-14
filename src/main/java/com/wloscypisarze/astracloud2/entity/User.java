package com.wloscypisarze.astracloud2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String role = "ROLE_USER";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "number_of_files", insertable = false, updatable = false)
    private Integer numberOfFiles;

    @Column(name = "total_usage_bytes", insertable = false, updatable = false)
    private Long totalUsageBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "limit_level_id", nullable = false)
    private LimitLevel limitLevel;
}
