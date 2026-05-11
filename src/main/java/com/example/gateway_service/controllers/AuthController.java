package com.example.gateway_service.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

  @GetMapping("/login")
  public String login() {
    return "redirect:/oauth2/authorization/google";
  }
}
