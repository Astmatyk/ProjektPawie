package com.wloscypisarze.astracloud2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "limit_levels")
@Getter
@Setter
@NoArgsConstructor
public class LimitLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "level_name", nullable = false, unique = true, length = 32)
    private String levelName;

    @Column(name = "max_bytes", nullable = false)
    private Long maxBytes;
}
