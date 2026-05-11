package com.example.gateway_service.config;

public enum TestUiLoginKey {

  SESSION_FLAG("TEST_UI_LOGIN"),
  STORAGE_KEY("pm_jwt_token");

  private final String value;

  TestUiLoginKey(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
