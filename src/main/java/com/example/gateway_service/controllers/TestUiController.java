package com.example.gateway_service.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.gateway_service.config.TestUiLoginKey;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class TestUiController {

  @GetMapping("/test-ui")
  public String testUi(Model model) {
    model.addAttribute("tokenStorageKey", TestUiLoginKey.STORAGE_KEY.value());
    return "test-console";
  }

  @GetMapping("/test-ui/login")
  public String initiateLogin(HttpServletRequest request) {
    request.getSession(true).setAttribute(TestUiLoginKey.SESSION_FLAG.value(), true);
    return "redirect:/oauth2/authorization/google";
  }
}
