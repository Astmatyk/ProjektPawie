package com.wloscypisarze.astracloud2.controller;

import com.wloscypisarze.astracloud2.dto.DeleteRequest;
import com.wloscypisarze.astracloud2.dto.RenameRequest;
import com.wloscypisarze.astracloud2.entity.FileObject;
import com.wloscypisarze.astracloud2.entity.Folder;
import com.wloscypisarze.astracloud2.entity.User;
import com.wloscypisarze.astracloud2.entity.SharedLink;
import com.wloscypisarze.astracloud2.repository.FileObjectRepository;
import com.wloscypisarze.astracloud2.repository.FolderRepository;
import com.wloscypisarze.astracloud2.repository.SharedLinkRepository;
import com.wloscypisarze.astracloud2.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api")
public class FileController {

    private final String UPLOAD_FOLDER = "uploads";
    private final FileObjectRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final SharedLinkRepository sharedLinkRepository;

    public FileController(FileObjectRepository fileRepository, FolderRepository folderRepository, UserRepository userRepository, SharedLinkRepository sharedLinkRepository) {
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
        this.sharedLinkRepository = sharedLinkRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "folderId", required = false) Long folderId,
                                        Principal principal) {

        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        //walidacja nazwy pliku
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        if (originalFilename.length() > 150) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Nazwa pliku jest za długa (maksymalnie 150 znaków)"
            ));
        }

        if (!file.isEmpty()) {
            String uuid = UUID.randomUUID().toString();
            Path filepath = Paths.get(UPLOAD_FOLDER, username, uuid+".bin");
            try {
                File userDir = new File(UPLOAD_FOLDER, username);
                if (!userDir.exists()) userDir.mkdirs();

                file.transferTo(filepath);

                //integracja z bazą danych
                FileObject fileObj = new FileObject();
                fileObj.setUser(user);
                fileObj.setFilename(originalFilename);
                fileObj.setStoragePath(filepath.toString());
                fileObj.setSize(file.getSize());

                if (folderId != null) {
                    Folder folder = folderRepository.findById(folderId).orElse(null);
                    //sprawdzenie czy folder należy do zalogowanego użytkownika
                    if (folder != null && folder.getUser().getId().equals(user.getId())) {
                        fileObj.setFolder(folder);
                    } else {
                        // Jeśli folder nie istnieje lub nie należy do usera, zapisujemy w root
                        fileObj.setFolder(null);
                    }
                }

                //w tym miejscu trigger sql sprawdza limity
                fileRepository.save(fileObj);

                return ResponseEntity.ok(Map.of("status", "success", "fileId", fileObj.getId(), "filename", originalFilename));
            } catch (DataIntegrityViolationException e) {
                // naruszenie UNIQUE (duplikat pliku w danym folderze)
                try {
                    Files.deleteIfExists(filepath);
                } catch (IOException ignored) {}

                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "status", "error",
                        "message", "Plik o takiej nazwie już istnieje w tym folderze."
                ));
            } catch (Exception e) {
                // łapiemy pozostałe błędy
                try {
                    Files.deleteIfExists(filepath);
                } catch (IOException ignored) {}

                // Sprawdzamy, czy znajduje się wiadomość z triggera
                Throwable rootCause = e;
                while (rootCause.getCause() != null && rootCause != rootCause.getCause()) {
                    rootCause = rootCause.getCause();
                }

                // weryfikujemy nasz kod błędu 45000
                if (rootCause.getMessage() != null && rootCause.getMessage().contains("User storage limit exceeded")) {
                    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                            "status", "error",
                            "message", "Brak miejsca na koncie."
                    ));
                }

                // inny, nieprzewidziany błąd
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "error", "Wystąpił nieoczekiwany błąd serwera."
                ));
            }
        }
        return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "No file uploaded"));
    }

    //zamiast dla usera to pobieramy pliki dla folderu. user jest wzięty z principal
    @GetMapping("/files")
    public ResponseEntity<?> listFiles(@RequestParam(value = "folderId", required = false) Long folderId, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        //pobieranie plikow z bd
        List<FileObject> files;
        if (folderId != null) {
            files = fileRepository.findByUserAndFolderId(user, folderId);
        } else {
            files = fileRepository.findByUserAndFolderIsNull(user);
        }

        //wcześniej był loop po plikach na dysku
        //teraz mamy response z bazy danych (lista "files")
        List<Map<String, Object>> response = new ArrayList<>();
        for (FileObject f : files) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("id", f.getId());
            stat.put("name", f.getFilename());
            stat.put("size", f.getSize());
            stat.put("extension", StringUtils.getFilenameExtension(f.getFilename()));
            stat.put("date", f.getModifiedAt() != null ? f.getModifiedAt().toEpochSecond(ZoneOffset.UTC) : null);
            response.add(stat);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteFile(@Valid @RequestBody DeleteRequest request, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        // plik ma należeć do wywołującego
        FileObject fileObj = fileRepository.findByIdAndUser(request.getId(), user).orElse(null);
        if (fileObj == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Nie znaleziono pliku"));
        }

        // usuwamy plik z dysku fizycznego
        File fileOnDisk = new File(fileObj.getStoragePath());
        boolean diskDeleted = !fileOnDisk.exists() || fileOnDisk.delete();

        if (diskDeleted) {
            // Usuwamy rekord z bazy danych (trigger w bazie zmniejszy user_storage)
            fileRepository.delete(fileObj);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Plik został usunięty"));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "error", "message", "Nie udało się usunąć pliku z dysku"));
    }

    @PostMapping("/folders/delete")
    public ResponseEntity<?> deleteFolder(@Valid @RequestBody DeleteRequest request, Principal principal) {
        try {
            String username = principal.getName();
            User user = userRepository.findByUsername(username).orElse(null);

            // query do bazy szukające po id i użytkowniku
            Folder folder = folderRepository.findByIdAndUser(request.getId(), user).orElse(null);
            if (folder == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", "error", "message", "Nie znaleziono folderu"));
            }

            // nasz wspaniały trigger w bazie usunie pliki samodzielnie
            folderRepository.delete(folder);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Folder został usunięty"));

        } catch (Exception e) {
            System.out.println("Folder się nie usunął");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Nie udało się usunąć folderu"));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchFiles(@RequestParam(value = "search", defaultValue = "") String query, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        List<FileObject> matchingFiles = fileRepository.findByUserAndFilenameContainingIgnoreCase(user, query);

        List<Map<String, Object>> response = new ArrayList<>();
        for (FileObject f : matchingFiles) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("id", f.getId());
            stat.put("name", f.getFilename());
            stat.put("size", f.getSize());
            stat.put("extension", StringUtils.getFilenameExtension(f.getFilename()));
            stat.put("date", f.getModifiedAt() != null ? f.getModifiedAt().toEpochSecond(ZoneOffset.UTC) : null);
            response.add(stat);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/rename")
    public ResponseEntity<?> renameFile(@Valid @RequestBody RenameRequest request, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        // Walidacja nowej nazwy
        String newName = StringUtils.cleanPath(request.getNewName());
        if (newName.contains("/") || newName.contains("..") || !StringUtils.hasText(newName)) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Niedozwolone znaki w nazwie pliku"));
        }

        // query do bazy o plik o danym id
        Long fileId = Long.valueOf(request.getId());
        FileObject fileObj = fileRepository.findByIdAndUser(fileId, user).orElse(null);
        if (fileObj == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Nie znaleziono pliku"));
        }

        // Sprawdzamy czy w tym samym katalogu nie ma już pliku o nowej nazwie
        boolean duplicateExists = fileObj.getFolder() != null
                ? fileRepository.existsByUserAndFolderIdAndFilename(user, fileObj.getFolder().getId(), newName)
                : fileRepository.existsByUserAndFolderIsNullAndFilename(user, newName);

        if (duplicateExists) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Plik o takiej nazwie już istnieje w tym folderze"));
        }

        try {
            fileObj.setFilename(newName);
            fileRepository.save(fileObj);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Nazwa pliku została zmieniona"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "error", "message", "Błąd zapisu bazy danych"));
        }
    }

    @PostMapping("/folders/rename")
    public ResponseEntity<?> renameFolder(@Valid @RequestBody RenameRequest request, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        // Walidacja
        String newName = StringUtils.cleanPath(request.getNewName());
        if (newName.contains("/") || newName.contains("..") || !StringUtils.hasText(newName)) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Niedozwolone znaki w nazwie folderu"));
        }

        // Pobieramy katalog po id i użytkowniku
        Long fileId = Long.valueOf(request.getId());
        Folder folder = folderRepository.findByIdAndUser(fileId, user).orElse(null);
        if (folder == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Nie znaleziono folder"));
        }

        // Sprawdzamy czy w katalogu wyżej nie ma już katalogu o nowej nazwie
        boolean duplicateExists = folder.getParent() != null
                ? folderRepository.existsByUserAndParentIdAndFoldername(user, folder.getParent().getId(), newName)
                : folderRepository.existsByUserAndParentIsNullAndFoldername(user, newName);

        if (duplicateExists) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Folder o takiej nazwie już tu istnieje"));
        }

        // zmieniamy jedynie foldername w BD
        try {
            folder.setFoldername(newName);
            folderRepository.save(folder);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Nazwa folderu została zmieniona"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "error", "message", "Błąd zapisu bazy danych"));
        }
    }

    // Endpoint pobierania plików
    @GetMapping("/download/{id}")
    public ResponseEntity<?> downloadFile(@PathVariable Long id, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        FileObject fileObj = fileRepository.findByIdAndUser(id, user).orElse(null);
        if (fileObj == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Plik nie istnieje lub brak dostępu"));
        }

        try {
            Path path = Paths.get(fileObj.getStoragePath());
            Resource resource = new UrlResource(path.toUri());

            if (resource.exists() || resource.isReadable()) {
                String contentType = Files.probeContentType(path);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileObj.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Plik fizyczny uszkodzony lub usunięty z serwera"));
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "error", "message", "Błąd generowania ścieżki pliku"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "error", "message", "Błąd odczytu typu pliku"));
        }
    }

    @PostMapping("/folders/create")
    public ResponseEntity<?> createFolder(@RequestBody Map<String, String> payload, Principal principal) {
        String folderName = payload.get("folderName");
        if (!StringUtils.hasText(folderName) || folderName.length() > 30) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Niepoprawna nazwa folderu"));
        }

        User user = userRepository.findByUsername(principal.getName()).orElseThrow();

        Folder folder = new Folder();
        folder.setUser(user);
        folder.setFoldername(folderName);

        if (payload.containsKey("parentId") && payload.get("parentId") != null && !payload.get("parentId").isBlank()) {
            Folder parent = folderRepository.findById(Long.valueOf(payload.get("parentId"))).orElse(null);
            if (parent != null && parent.getUser().getId().equals(user.getId())) {
                folder.setParent(parent);
            }
        }

        try {
            folderRepository.save(folder);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Utworzono folder"));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Taki folder już istnieje w tym miejscu"));
        }
    }

    //znajduje foldery dla danego parentId
    @GetMapping("/folders")
    public ResponseEntity<?> listFolders(@RequestParam(value = "parentId", required = false) Long parentId, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        List<Folder> folders;
        if (parentId != null) {
            folders = folderRepository.findByUserAndParentId(user, parentId);
        } else {
            folders = folderRepository.findByUserAndParentIsNull(user);
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (Folder f : folders) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("id", f.getId());
            stat.put("name", f.getFoldername());
            stat.put("type", "folder"); // nowy typ dla frontendu
            response.add(stat);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/usage")
    public ResponseEntity<?> getStorageUsage(Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        long usedBytes = user.getTotalUsageBytes() != null ? user.getTotalUsageBytes() : 0L;
        long maxBytes = user.getLimitLevel().getMaxBytes();
        String planName = user.getLimitLevel().getLevelName();

        return ResponseEntity.ok(Map.of(
                "usedBytes", usedBytes,
                "maxBytes", maxBytes,
                "planName", planName
        ));
    }

    @PostMapping("/share/{id}")
    public ResponseEntity<?> shareFile(@PathVariable Long id, Principal principal) {
        String username = principal.getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        FileObject fileObj = fileRepository.findByIdAndUser(id, user).orElse(null);
        if (fileObj == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Nie znaleziono pliku"));
        }

        // Sprawdzamy czy link już istnieje
        SharedLink sharedLink = sharedLinkRepository.findByFile(fileObj).orElse(null);
        if (sharedLink == null) {
            sharedLink = new SharedLink();
            sharedLink.setFile(fileObj);
            sharedLink.setToken(UUID.randomUUID().toString());
            sharedLinkRepository.save(sharedLink);
        }

        return ResponseEntity.ok(Map.of("status", "ok", "token", sharedLink.getToken()));
    }

    @GetMapping("/share/{token}")
    public ResponseEntity<?> getSharedFile(@PathVariable String token) {
        SharedLink sharedLink = sharedLinkRepository.findByToken(token).orElse(null);

        if (sharedLink == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Link nie istnieje lub wygasł");
        }

        // Sprawdzanie wygaśnięcia linku
        if (sharedLink.getExpiresAt() != null && sharedLink.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.GONE).body("Link wygasł");
        }

        FileObject fileObj = sharedLink.getFile();
        Path path = Paths.get(fileObj.getStoragePath());
        Resource resource;
        try {
            resource = new UrlResource(path.toUri());
        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Zwiększamy licznik pobrań
        sharedLink.setDownloadCount(sharedLink.getDownloadCount() + 1);
        sharedLinkRepository.save(sharedLink);

        String contentType;
        try {
            contentType = Files.probeContentType(path);
        } catch (IOException e) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileObj.getFilename() + "\"")
                .body(resource);
    }
}
