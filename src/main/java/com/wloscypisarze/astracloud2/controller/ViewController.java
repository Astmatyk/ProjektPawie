package com.wloscypisarze.astracloud2.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class ViewController {
    @GetMapping("/")
    public String index(Model model, Principal principal) {
        return "index";
    }

    @GetMapping("/account")
    public String getAccountPage(Principal principal, Model model) {
        //nie dostaniemy sie tu bez zalogowania wiec nie wrzutam tu if null xd
        //w założeniu thymeleaf powinien to potem czytać ale jeszcze nie czyta
        model.addAttribute("username", principal.getName());

        return "account";
    }
}
