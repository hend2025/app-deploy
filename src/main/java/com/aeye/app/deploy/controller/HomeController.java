package com.aeye.app.deploy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class HomeController {

    @GetMapping
    public String home(Model model) {return "verMgt"; }

    @GetMapping("/appMgt")
    public String appManage(Model model) {
        return "appMgt";
    }

    @GetMapping("/logMgt")
    public String apps(Model model) {
        return "logMgt";
    }

    @GetMapping("/about")
    public String about(Model model) {
        return "about";
    }

}
