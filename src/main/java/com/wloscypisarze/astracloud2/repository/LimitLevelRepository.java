package com.wloscypisarze.astracloud2.repository;

import com.wloscypisarze.astracloud2.entity.LimitLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LimitLevelRepository extends JpaRepository<LimitLevel, Long> {
    Optional<LimitLevel> findByLevelName(String levelName);
}