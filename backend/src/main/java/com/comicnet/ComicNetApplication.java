package com.comicnet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    excludeName =
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class ComicNetApplication {
  public static void main(String[] args) {
    SpringApplication.run(ComicNetApplication.class, args);
  }
}
