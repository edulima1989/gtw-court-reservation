package com.courtreservation.gateway.config;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PreflightCorsFilter extends OncePerRequestFilter {

  private final CorsProperties corsProperties;

  public PreflightCorsFilter(CorsProperties corsProperties) {
    this.corsProperties = corsProperties;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!HttpMethod.OPTIONS.matches(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    String origin = request.getHeader(HttpHeaders.ORIGIN);
    String requestedMethod = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
    if (!StringUtils.hasText(origin) || !StringUtils.hasText(requestedMethod)) {
      filterChain.doFilter(request, response);
      return;
    }

    if (!isAllowedOrigin(origin)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    response.setHeader(HttpHeaders.VARY, String.join(", ",
        HttpHeaders.ORIGIN,
        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS));
    response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, String.join(", ", corsProperties.getAllowedMethods()));
    response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, resolveAllowedHeaders(request));
    response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, String.valueOf(corsProperties.getMaxAgeSeconds()));
    if (corsProperties.isAllowCredentials()) {
      response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }
    response.setStatus(HttpServletResponse.SC_OK);
  }

  private boolean isAllowedOrigin(String origin) {
    List<String> allowedOrigins = corsProperties.getAllowedOrigins();
    return allowedOrigins != null && allowedOrigins.stream().anyMatch(origin::equals);
  }

  private String resolveAllowedHeaders(HttpServletRequest request) {
    List<String> configuredHeaders = corsProperties.getAllowedHeaders();
    if (configuredHeaders == null || configuredHeaders.isEmpty()) {
      return "";
    }

    boolean wildcard = configuredHeaders.stream().anyMatch("*"::equals);
    if (!wildcard) {
      return String.join(", ", configuredHeaders);
    }

    String requestedHeaders = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
    if (StringUtils.hasText(requestedHeaders)) {
      return requestedHeaders;
    }

    return "*";
  }
}
