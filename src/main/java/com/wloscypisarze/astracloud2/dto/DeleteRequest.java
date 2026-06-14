package com.wloscypisarze.astracloud2.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

public class DeleteRequest {
    @Getter
    @Setter
    @NotNull(message = "Wymagane ID pliku")
    private Long id;
}
