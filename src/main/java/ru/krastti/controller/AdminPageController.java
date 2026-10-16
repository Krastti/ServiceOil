package ru.krastti.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Страницы входа и главная страница администратора используют локальные статические ресурсы. */
@Controller
public class AdminPageController {
    @GetMapping("/login")
    public String login() { return "forward:/login.html"; }

    @GetMapping("/admin")
    public String dashboard() { return "forward:/index.html"; }
}
