package com.wloscypisarze.astracloud2.dto;

import lombok.Getter;
import lombok.Setter;

public class AdminUserEditRequest {
    @Getter
    @Setter
    //todo: dodac walidacje
    private String email;

    @Getter
    @Setter
    private String subscription;

    @Getter
    @Setter
    private String password;
}