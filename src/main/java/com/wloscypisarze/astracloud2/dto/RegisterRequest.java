package com.wloscypisarze.astracloud2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

public class RegisterRequest {
    // Getters and Setters
    @Getter
    @Setter
    @NotBlank(message = "Login jest wymagany")
    @Size(min = 2, max = 30, message = "Login musi mieć od 2 do 30 znaków")
    private String username;

    @Setter
    @Getter
    @NotBlank(message = "Adres e-mail jest wymagany")
    @Email(message = "Niepoprawny format adresu e-mail")
    private String email;

    @Setter
    @Getter
    @NotBlank(message = "Hasło jest wymagane")
    @Size(min = 8, message = "Hasło musi mieć co najmniej 8 znaków")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
            message = "Hasło musi zawierać co najmniej 8 znaków, w tym cyfrę i znak specjalny"
    )
    private String password;

    @Setter
    @Getter
    @NotBlank(message = "Potwierdzenie hasła jest wymagane")
    @JsonProperty("passwordConfirm")
    private String passwordConfirm;

}
