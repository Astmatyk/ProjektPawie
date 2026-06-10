package com.wloscypisarze.astracloud2.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/theme")
public class ThemeController {

    @PostMapping("/switch")
    public String switchTheme(@RequestParam String theme,
                              HttpSession session,
                              HttpServletRequest request) {
        // dozwolone motywy
        if (!List.of("dark-theme", "light-theme", "high-contrast").contains(theme)) {
            theme = "dark-theme"; // domyślny
        }
        session.setAttribute("theme", theme);

        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null && !referer.isEmpty() ? referer : "/");
    }
}