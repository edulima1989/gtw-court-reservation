package com.courtreservation.gateway.security;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "security.external-auth")
public class ExternalAuthProperties {

  private String validationUrl;

  private List<String> protectedPaths = new ArrayList<>();

  private List<String> excludedPaths = new ArrayList<>();
}
