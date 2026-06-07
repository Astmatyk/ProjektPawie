package com.wloscypisarze.astracloud2.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserDetailsManager userDetailsManager;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserDetailsManager userDetailsManager, PasswordEncoder passwordEncoder) {
        this.userDetailsManager = userDetailsManager;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> data) {
        String login = data.get("username");
        String password = data.get("password");

        if (login == null || password == null || login.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Login i hasło są wymagane"));
        }

        if (userDetailsManager.userExists(login)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Login już istnieje"));
        }

        // tworzenie użytkownika w spring security
        UserDetails newUser = User.builder()
                .username(login)
                .password(passwordEncoder.encode(password))
                .roles("USER")
                .build();

        userDetailsManager.createUser(newUser);

        new java.io.File("uploads/" + login).mkdirs();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Rejestracja zakończona pomyślnie"));
    }

    // endpoint dla frontendu, żeby wiedział, kto jest zalogowany
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("login", principal.getName()));
    }
}