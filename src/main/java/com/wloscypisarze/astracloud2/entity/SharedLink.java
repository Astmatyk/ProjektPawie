package com.wloscypisarze.astracloud2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "shared_links")
@Getter
@Setter
@NoArgsConstructor
public class SharedLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileObject file;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}