package com.wloscypisarze.astracloud2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "files")
@Getter
@Setter
@NoArgsConstructor
public class FileObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(nullable = false)
    private Long size;

    @Column(name = "modified_at", insertable = false, updatable = false)
    private LocalDateTime modifiedAt;
}