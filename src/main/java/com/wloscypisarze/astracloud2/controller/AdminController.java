package com.wloscypisarze.astracloud2.controller;

import com.wloscypisarze.astracloud2.dto.AdminUserEditRequest;
import com.wloscypisarze.astracloud2.entity.LimitLevel;
import com.wloscypisarze.astracloud2.entity.User;
import com.wloscypisarze.astracloud2.repository.LimitLevelRepository;
import com.wloscypisarze.astracloud2.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final LimitLevelRepository limitLevelRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepository, LimitLevelRepository limitLevelRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.limitLevelRepository = limitLevelRepository;
        this.passwordEncoder = passwordEncoder;
    }


    @GetMapping("/dashboard")
    public String adminDashboard(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        // pobieranie użytkowników z bazy do wyświetlenia
        List<User> allUsers = userRepository.findAll();
        List<Map<String, Object>> userRows = new ArrayList<>();

        for (User u : allUsers) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", u.getId());
            row.put("username", u.getUsername());
            row.put("email", u.getEmail());
            row.put("role", u.getRole());

            // wyciągamy statystyki zliczane przez  triggery bazodanowe
            row.put("fileCount", u.getNumberOfFiles());

            // bajty storage przeliczone na MB dla czytelności
            double usageInMb = (double) u.getTotalUsageBytes() / (1024 * 1024);
            row.put("storageUsage", String.format("%.2f", usageInMb));

            // Nazwa pakietu / subskrypcji
            row.put("subscription", u.getLimitLevel().getLevelName());

            userRows.add(row);
        }

        model.addAttribute("usersList", userRows);
        return "admin-dashboard"; // nazwa pliku HTML w templates
    }

    //SAMUEL START
    // --- API: EDYCJA UŻYTKOWNIKA PRZEZ ADMINA ---
    @PostMapping("/api/users/{id}")
    @ResponseBody
    public ResponseEntity<?> editUserByAdmin(@PathVariable("id") Long id, @RequestBody AdminUserEditRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Użytkownik nie istnieje"));

        // 1. Zmiana adresu e-mail
        user.setEmail(request.getEmail());

        // 2. Zmiana pakietu (Subskrypcji)
        LimitLevel newLevel = limitLevelRepository.findByLevelName(request.getSubscription())
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono poziomu limitu: " + request.getSubscription()));
        user.setLimitLevel(newLevel);

        // 3. Opcjonalna zmiana hasła (tylko jeśli admin coś wpisał)
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    // --- API: USUWANIE UŻYTKOWNIKA PRZEZ ADMINA ---
    @DeleteMapping("/api/users/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteUserByAdmin(@PathVariable("id") Long id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        userRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    //SAMUEL END
}