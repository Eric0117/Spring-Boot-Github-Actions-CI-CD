package com.example.cicd.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author Eric
 * @Description
 * @Since 22. 9. 12.
 **/
@RestController
@RequestMapping("/api/v1/main")
public class MainController {

    @Value("${env.name}")
    private String envName;

    @GetMapping
    public String main() {
        return "Hello World! V4 => " + envName;
    }
}
