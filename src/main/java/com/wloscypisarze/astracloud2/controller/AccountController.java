package com.wloscypisarze.astracloud2.controller;

import com.wloscypisarze.astracloud2.entity.User;
import com.wloscypisarze.astracloud2.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class AccountController {
    private final UserRepository userRepository;

    public AccountController(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    @GetMapping("/account-data")
    public String accountData(Principal principal, Model model){
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow();
        return "account-data";
    }
}
