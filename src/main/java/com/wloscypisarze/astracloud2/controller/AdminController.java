package com.wloscypisarze.astracloud2.controller;

import com.wloscypisarze.astracloud2.entity.User;
import com.wloscypisarze.astracloud2.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
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
}