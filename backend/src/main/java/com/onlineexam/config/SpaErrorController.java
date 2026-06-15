package com.onlineexam.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.ErrorPageRegistrar;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * SPA fallback via ErrorController: when a 404 occurs for a non-API path,
 * serve index.html with a 200 status so Vue Router can handle client-side navigation.
 *
 * This approach avoids the priority conflict between @RequestMapping("/**")
 * and Spring's resource handlers. The ErrorController only fires AFTER all
 * other handlers (resource, API) have failed to match.
 */
@Controller
public class SpaErrorController implements ErrorController {

  @RequestMapping("/error")
  public void handleError(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
        response.setStatus(200);
        response.setContentType("text/html");
        // Read index.html from classpath
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("static/index.html")) {
          if (in != null) {
            byte[] bytes = in.readAllBytes();
            response.getOutputStream().write(bytes);
            return;
          }
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
    response.setStatus(statusCode);
    response.setContentType("application/json");
    response.getOutputStream().write(
      ("{\"status\":" + statusCode + ",\"error\":\"" + errorPhrase + "\"}").getBytes(StandardCharsets.UTF_8)
    );
  }
}
