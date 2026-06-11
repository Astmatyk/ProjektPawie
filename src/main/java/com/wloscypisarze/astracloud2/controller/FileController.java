package com.wloscypisarze.astracloud2.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api")
public class FileController {

    private final String UPLOAD_FOLDER = "uploads";

    private String hasher(String filename) {
        return DigestUtils.md5DigestAsHex(filename.getBytes(StandardCharsets.UTF_8));
    }

    private String dehasher(String id, String user) {
        File dir = new File(UPLOAD_FOLDER, user);
        if (dir.exists() && dir.isDirectory()) {
            for (File file : dir.listFiles()) {
                if (hasher(file.getName()).equals(id)) {
                    return file.getName();
                }
            }
        }
        return null;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, Principal principal) {
        String user = principal.getName();

        //walidacja nazwy pliku
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        if (originalFilename.length() > 150) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Nazwa pliku jest za długa (maksymalnie 150 znaków)"
            ));
        }

        if (!file.isEmpty()) {
            try {
                File userDir = new File(UPLOAD_FOLDER, user);
                if (!userDir.exists()) userDir.mkdirs();

                Path filepath = Paths.get(UPLOAD_FOLDER, user, StringUtils.cleanPath(file.getOriginalFilename()));
                file.transferTo(filepath);
                return ResponseEntity.ok(Map.of("status", "success", "filename", file.getOriginalFilename()));
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
            }
        }
        return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "No file uploaded"));
    }

    @GetMapping("/files/{user}")
    public ResponseEntity<?> listFiles(@PathVariable String user, Principal principal) {
        String loggedInUser = principal.getName();
        if (!user.equals(loggedInUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
        }

        List<Map<String, Object>> fileInfos = new ArrayList<>();
        File userDir = new File(UPLOAD_FOLDER, user);

        if (userDir.exists() && userDir.isDirectory()) {
            for (File file : userDir.listFiles()) {
                if (file.isFile()) {
                    String name = file.getName();
                    Map<String, Object> stat = new HashMap<>();
                    stat.put("id", hasher(name));
                    stat.put("name", name);
                    stat.put("size", file.length());
                    stat.put("extension", name.substring(name.lastIndexOf(".") + 1).toLowerCase());
                    stat.put("date", file.lastModified() / 1000);
                    fileInfos.add(stat);
                }
            }
        }
        return ResponseEntity.ok(fileInfos);
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteFile(@RequestBody Map<String, String> data, Principal principal) {
        String user = principal.getName();
        String id = data.get("id");

        String filename = dehasher(id, user);
        if (filename == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "File not found"));
        }

        // cleanpath zabezpiecza przed ../
        filename = StringUtils.cleanPath(filename);
        File file = new File(UPLOAD_FOLDER + "/" + user + "/" + filename);

        if (file.exists() && file.delete()) {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "not found"));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchFiles(@RequestParam(value = "search", defaultValue = "") String query, Principal principal) {
        String user = principal.getName();
        query = query.toLowerCase();

        List<Map<String, Object>> matchingFiles = new ArrayList<>();
        File userDir = new File(UPLOAD_FOLDER, user);

        if (userDir.exists() && userDir.isDirectory()) {
            for (File file : userDir.listFiles()) {
                if (file.isFile() && file.getName().toLowerCase().contains(query)) {
                    Map<String, Object> stat = new HashMap<>();
                    stat.put("id", hasher(file.getName()));
                    stat.put("name", file.getName());
                    stat.put("size", file.length());
                    stat.put("extension", file.getName().substring(file.getName().lastIndexOf(".") + 1).toLowerCase());
                    stat.put("date", file.lastModified() / 1000);
                    matchingFiles.add(stat);
                }
            }
        }
        return ResponseEntity.ok(matchingFiles);
    }

    @PostMapping("/rename")
    public ResponseEntity<?> renameFile(@Valid @RequestBody RenameRequest request, Principal principal) {
        String user = principal.getName();
        String id = request.getId();
        String newName = request.getNewName();

        //
        newName = StringUtils.cleanPath(newName);
        if (newName.contains("/") || newName.contains("..")) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Niedozwolone znaki w nazwie pliku"));
        }

        String oldFilename = dehasher(id, user);
        if (oldFilename == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "error", "message", "Nie znaleziono pliku"));
        }

        File oldFile = new File(UPLOAD_FOLDER + "/" + user + "/" + oldFilename);
        File newFile = new File(UPLOAD_FOLDER + "/" + user + "/" + newName);

        if (newFile.exists()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Plik o takiej nazwie już istnieje"));
        }

        if (oldFile.exists() && oldFile.renameTo(newFile)) {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "error", "message", "Nie udało się zmienić nazwy pliku"));
    }
}