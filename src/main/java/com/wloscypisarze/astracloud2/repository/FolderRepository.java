package com.wloscypisarze.astracloud2.repository;

import com.wloscypisarze.astracloud2.entity.Folder;
import com.wloscypisarze.astracloud2.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByUserAndParentIsNull(User user);

    List<Folder> findByUserAndParentId(User user, Long parentId);
}