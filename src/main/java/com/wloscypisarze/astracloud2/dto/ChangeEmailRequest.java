package com.wloscypisarze.astracloud2.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

public class ChangeEmailRequest {
    @Getter
    @Setter
    @NotBlank(message = "Adres e-mail jest wymagany")
    @Email(message = "Niepoprawny format adresu e-mail")
    private String newEmail;
}
