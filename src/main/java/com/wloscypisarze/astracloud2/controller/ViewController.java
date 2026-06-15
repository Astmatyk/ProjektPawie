package com.wloscypisarze.astracloud2.controller;

import com.wloscypisarze.astracloud2.entity.SharedLink;
import com.wloscypisarze.astracloud2.entity.User;
import com.wloscypisarze.astracloud2.repository.SharedLinkRepository;
import com.wloscypisarze.astracloud2.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Controller
public class ViewController {

    private final UserRepository userRepository;
    private final SharedLinkRepository sharedLinkRepository;

    public ViewController(SharedLinkRepository sharedLinkRepository, UserRepository userRepository) {
        this.userRepository = userRepository;
        this.sharedLinkRepository = sharedLinkRepository;
    }

    @GetMapping("/")
    public String index(Model model, Principal principal) {
        return "index";
    }

    @GetMapping("/account")
    public String getAccountPage(Principal principal, Model model) {
        model.addAttribute("username", principal.getName());
        return "account";
    }

    @GetMapping("/register")
    public String getRegisterPage(Principal principal, Model model) {
        if (principal != null) {
            return "redirect:/account";
        }
        return "register";
    }

    @GetMapping("/login")
    public String getLoginPage(@RequestParam(value = "error", required = false) String error, Principal principal, Model model) {
        if (principal != null) {
            return "redirect:/account";
        }

        if (error != null) {
            model.addAttribute("errorMessage", "Nieprawidłowy login lub hasło.");
        }
        return "login";
    }

    @GetMapping("/share/{id}")
    public String getSharePage(Principal principal, Model model, @PathVariable String id) {
        model.addAttribute("token", id);

        //potrzebujemy filename z bazy danych
        Optional<SharedLink> linkOpt = sharedLinkRepository.findByToken(id);

        if (linkOpt.isPresent()) {
            SharedLink sharedLink = linkOpt.get();

            // Sprawdzamy czy link nie wygasł
            if (sharedLink.getExpiresAt() != null && sharedLink.getExpiresAt().isBefore(LocalDateTime.now())) {
                model.addAttribute("errorMessage", "Ten link już wygasł.");
            } else {
                //Dodajemy nazwę pliku do modelu.
                model.addAttribute("filename", sharedLink.getFilename());
                if (sharedLink.getExpiresAt() != null) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
                    String formattedExpirationDate = sharedLink.getExpiresAt().format(formatter);
                    model.addAttribute("expiresAt", formattedExpirationDate);
                }
            }
        } else {
            model.addAttribute("errorMessage", "Link nie istnieje lub został usunięty.");
        }

        return "share";
    }

    @GetMapping("/manageAccount")
    public String accountData(Principal principal, Model model){
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow();

        model.addAttribute("loggedUser", user);

        return "manageAccount";
    }
}