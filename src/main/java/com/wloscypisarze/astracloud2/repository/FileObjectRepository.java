package com.wloscypisarze.astracloud2.repository;

import com.wloscypisarze.astracloud2.entity.FileObject;
import com.wloscypisarze.astracloud2.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FileObjectRepository extends JpaRepository<FileObject, Long> {

    List<FileObject> findByUserAndFolderId(User user, Long folderId);

    List<FileObject> findByUserAndFolderIsNull(User user);

    List<FileObject> findByUserAndFilenameContainingIgnoreCase(User user, String filename);

    Optional<FileObject> findByIdAndUser(Long id, User user);

    boolean existsByUserAndFolderIdAndFilename(User user, Long folderId, String filename);
    boolean existsByUserAndFolderIsNullAndFilename(User user, String filename);
}