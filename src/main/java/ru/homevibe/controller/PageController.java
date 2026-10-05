package ru.homevibe.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping({"/admin", "/admin/"})
    public String admin() { return "forward:/admin.html"; }

    @GetMapping({"/account", "/account/"})
    public String account() { return "forward:/account.html"; }
}
