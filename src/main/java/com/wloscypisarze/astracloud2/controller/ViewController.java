package com.wloscypisarze.astracloud2.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class ViewController {
    @GetMapping("/")
    public String index(Model model, Principal principal) {
        return "index";
    }

    @GetMapping("/account")
    public String getAccountPage(Principal principal, Model model) {
        //w założeniu thymeleaf powinien to potem czytać ale jeszcze nie czyta
        model.addAttribute("username", principal.getName());

        return "account";
    }

    @GetMapping("/register")
    public String getRegisterPage(Principal principal, Model model) {
        if (principal != null) {
            return "redirect:/account";
        }

        return "register";
    }

    @GetMapping("/login")
    public String getLoginPage(@RequestParam(value = "error", required = false) String error, Principal principal, Model model) {
        if (principal != null) {
            return "redirect:/account";
        }

        if (error != null) {
            model.addAttribute("errorMessage", "Nieprawidłowy login lub hasło.");
        }

        return "login";
    }
}
