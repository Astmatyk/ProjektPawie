package com.wloscypisarze.astracloud2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

public class RenameRequest {
    @Getter
    @Setter
    @NotBlank(message = "Wymagane ID pliku")
    private String id;

    @Getter
    @Setter
    @NotBlank(message = "Nazwa pliku nie może być pusta")
    @Size(max = 150, message = "Nazwa pliku jest za długa (150 znaków max)")
    private String newName;
}
