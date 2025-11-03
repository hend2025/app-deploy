package com.aeye.app.deploy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class HomeController {

    // 所有前端路由都返回index.html，由Vue Router处理
    @GetMapping(value = {"", "/", "/appMgt", "/logMgt", "/about"})
    public String index() {
        return "forward:/index.html";
    }

}
