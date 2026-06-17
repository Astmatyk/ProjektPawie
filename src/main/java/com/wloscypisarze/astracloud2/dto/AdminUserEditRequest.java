package com.wloscypisarze.astracloud2.dto;

import lombok.Getter;
import lombok.Setter;

public class AdminUserEditRequest {
    @Getter
    @Setter
    @NotBlank(message = "Adres e-mail jest wymagany")
    @Email(message = "Niepoprawny format adresu e-mail")
    private String email;

    @Getter
    @Setter
    private String subscription;

    @Getter
    @Setter
    @NotBlank(message = "Hasło jest wymagane")
    @Size(min = 8, message = "Nowe hasło musi mieć co najmniej 8 znaków")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
            message = "Hasło musi zawierać co najmniej 8 znaków, w tym cyfrę i znak specjalny"
    )
    private String password;
}