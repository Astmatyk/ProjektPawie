package com.wloscypisarze.astracloud2.repository;

import com.wloscypisarze.astracloud2.entity.FileObject;
import com.wloscypisarze.astracloud2.entity.SharedLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SharedLinkRepository extends JpaRepository<SharedLink, Long> {
    Optional<SharedLink> findByToken(String token);
    Optional<SharedLink> findByFile(FileObject file);
}
