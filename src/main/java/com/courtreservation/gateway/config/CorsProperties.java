package com.courtreservation.gateway.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
@Getter
@Setter
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

  private List<String> allowedOrigins = new ArrayList<>();

  private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

  private List<String> allowedHeaders = List.of("*");

  private long maxAgeSeconds = 3600;

  private boolean allowCredentials = true;
}
