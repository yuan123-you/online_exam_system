package com.onlineexam.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * SPA fallback via ErrorController: when a 404 occurs for a non-API path,
 * serve index.html with a 200 status so Vue Router can handle client-side navigation.
 *
 * This approach avoids the priority conflict between @RequestMapping("/**")
 * and Spring's resource handlers. The ErrorController only fires AFTER all
 * other handlers (resource, API) have failed to match.
 */
@RestController
public class SpaErrorController implements ErrorController {

  @RequestMapping("/error")
  public ResponseEntity<?> handleError(HttpServletRequest request) throws IOException {
    Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int statusCode;
    try {
      statusCode = statusObj != null ? Integer.parseInt(statusObj.toString()) : 500;
    } catch (NumberFormatException e) {
      statusCode = 500;
    }

    if (statusCode == HttpStatus.NOT_FOUND.value()) {
      Object pathObj = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
      String path = pathObj instanceof String s ? s : null;
      // Only serve index.html for SPA routes (not API calls or missing static assets)
      if (path != null && !path.startsWith("/api/") && !path.startsWith("/assets/")) {
        ClassPathResource indexHtml = new ClassPathResource("static/index.html");
        if (indexHtml.exists()) {
          byte[] content = indexHtml.getInputStream().readAllBytes();
          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.TEXT_HTML);
          return new ResponseEntity<>(content, headers, HttpStatus.OK);
        }
      }
    }
    // For all other errors, return a JSON error with matching status description
    String errorPhrase;
    try {
      errorPhrase = HttpStatus.valueOf(statusCode).getReasonPhrase();
    } catch (IllegalArgumentException e) {
      errorPhrase = "Internal Server Error";
    }
    return ResponseEntity.status(statusCode)
        .contentType(MediaType.APPLICATION_JSON)
        .body(("{\"status\":" + statusCode + ",\"error\":\"" + errorPhrase + "\"}").getBytes(StandardCharsets.UTF_8));
  }
}
