package com.courtreservation.gateway.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public interface ReservationsOwnershipGatewayFilterFunctions {

  Pattern ACTIVE_USER_PATH = Pattern.compile("^/reservations/api/reservations/active/user/([^/]+)$");
  Pattern CANCELED_USER_PATH = Pattern.compile("^/reservations/api/reservations/canceled/user/([^/]+)$");
  List<String> OWNERSHIP_CHECK_METHODS = List.of(HttpMethod.GET.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name());
  List<String> ROLE_CLAIM_KEYS = List.of("userRole", "role");
  List<String> USER_ID_CLAIM_KEYS = List.of("userId", "id", "sub");
  ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

  @Shortcut
  static HandlerFilterFunction<ServerResponse, ServerResponse> reservationsOwnership() {
    return (request, next) -> {
      if (!OWNERSHIP_CHECK_METHODS.contains(request.method().name())) {
        return next.handle(request);
      }

      String path = request.path();
      String pathUserId = extractPathUserId(path);
      if (!StringUtils.hasText(pathUserId)) {
        return next.handle(request);
      }

      String authorization = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
      if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
        return error(request, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
      }

      Map<String, Object> claims;
      try {
        claims = decodeClaims(authorization.substring("Bearer ".length()).trim());
      } catch (Exception ex) {
        return error(request, HttpStatus.UNAUTHORIZED, "Invalid token");
      }

      String userRole = getStringClaim(claims, ROLE_CLAIM_KEYS);
      if (!"USUARIO_FINAL".equals(userRole)) {
        return next.handle(request);
      }

      String tokenUserId = getStringClaim(claims, USER_ID_CLAIM_KEYS);
      if (!StringUtils.hasText(tokenUserId) || !pathUserId.equals(tokenUserId)) {
        return error(request, HttpStatus.FORBIDDEN, "USUARIO_FINAL can only modify its own records");
      }

      return next.handle(request);
    };
  }

  private static String extractPathUserId(String path) {
    Matcher activeMatcher = ACTIVE_USER_PATH.matcher(path);
    if (activeMatcher.matches()) {
      return activeMatcher.group(1);
    }

    Matcher canceledMatcher = CANCELED_USER_PATH.matcher(path);
    if (canceledMatcher.matches()) {
      return canceledMatcher.group(1);
    }

    return null;
  }

  private static Map<String, Object> decodeClaims(String token) throws Exception {
    String[] jwtParts = token.split("\\.");
    if (jwtParts.length < 2) {
      throw new IllegalArgumentException("Invalid token");
    }

    byte[] decodedPayload = Base64.getUrlDecoder().decode(jwtParts[1]);
    String payloadJson = new String(decodedPayload, StandardCharsets.UTF_8);
    return OBJECT_MAPPER.readValue(payloadJson, new TypeReference<>() {
    });
  }

  private static String getStringClaim(Map<String, Object> claims, List<String> keys) {
    for (String key : keys) {
      Object value = claims.get(key);
      if (value == null) {
        continue;
      }
      String claimValue = String.valueOf(value).trim();
      if (StringUtils.hasText(claimValue)) {
        return claimValue;
      }
    }
    return null;
  }

  private static ServerResponse error(ServerRequest request, HttpStatus status, String message) {
    ServerResponse.BodyBuilder builder = ServerResponse.status(status)
        .contentType(MediaType.APPLICATION_JSON);

    String origin = request.headers().firstHeader(HttpHeaders.ORIGIN);
    if (StringUtils.hasText(origin)) {
      builder.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
      builder.header(HttpHeaders.VARY, HttpHeaders.ORIGIN);
    }

    return builder.body("{\"error\":\"" + message + "\"}");
  }
}
