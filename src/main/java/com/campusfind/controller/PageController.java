package com.campusfind.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping({"/", "/login", "/admin/login", "/register", "/forgot-password", "/reset-password", "/verify-email", "/dashboard", "/items", "/items/{id}", "/report/lost", "/report/found", "/my-items", "/assistant", "/claims", "/claims/{id}", "/notifications", "/profile", "/admin"})
    public String page(Model model) {
        model.addAttribute("pageTitle", "Lost on Campus. Found with Intelligence.");
        return "index";
    }
}
