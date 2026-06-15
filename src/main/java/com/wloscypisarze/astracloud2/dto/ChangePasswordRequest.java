package com.wloscypisarze.astracloud2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

public class ChangePasswordRequest {

    @Getter
    @Setter
    @NotBlank(message = "Stare hasło jest wymagane")
    private String oldPassword;

    @Getter
    @Setter
    @NotBlank(message = "Hasło jest wymagane")
    @Size(min = 8, message = "Nowe hasło musi mieć co najmniej 8 znaków")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
            message = "Hasło musi zawierać co najmniej 8 znaków, w tym cyfrę i znak specjalny"
    )
    private String newPassword;

}
