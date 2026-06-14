package com.wloscypisarze.astracloud2.controller;

import com.wloscypisarze.astracloud2.dto.RegisterRequest;
import com.wloscypisarze.astracloud2.entity.LimitLevel;
import com.wloscypisarze.astracloud2.entity.User;
import com.wloscypisarze.astracloud2.repository.LimitLevelRepository;
import com.wloscypisarze.astracloud2.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserRepository userRepository;
    private final LimitLevelRepository limitLevelRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, LimitLevelRepository limitLevelRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.limitLevelRepository = limitLevelRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, BindingResult bindingResult) {

        //błędy walidacyjne
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().get(0).getDefaultMessage();
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
        }

        //hasła muszą się zgadzać
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Hasła nie są identyczne"));
        }

        //niedozwolone są duplikaty użytkowników
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Login już istnieje"));
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email jest już w użyciu"));
        }

        // Domyślnie FREE
        LimitLevel freeLevel = limitLevelRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Błąd: Nie przypisano poziomu"));

        // tworzenie użytkownika - używamy naszej encji User zamiast spring security(!)
        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setEmail(request.getEmail());
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setRole("ROLE_USER");
        newUser.setLimitLevel(freeLevel);

        userRepository.save(newUser);

        // Tworzenie katalogu
        new java.io.File("uploads/" + request.getUsername()).mkdirs();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Rejestracja zakończona pomyślnie"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("login", principal.getName()));
    }
}
