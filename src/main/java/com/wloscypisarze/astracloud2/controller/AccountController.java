package com.wloscypisarze.astracloud2.controller;

import com.wloscypisarze.astracloud2.entity.User;
import com.wloscypisarze.astracloud2.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class AccountController {
    private final UserRepository userRepository;

    public AccountController(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    @GetMapping("/account-data")
    public String accountData(Principal principal, Model model){
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow();

        model.addAttribute("loggedUser", user);

        return "account-data";
    }

    @PostMapping("/account-data/update-email")
    public String updateEmail(@RequestParam("newEmail") String newEmail, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Użytkownik nie istnieje"));

        // Aktualizacja i zapis w bazie
        user.setEmail(newEmail);
        userRepository.save(user);

        return "redirect:/account-data";
    }
}
