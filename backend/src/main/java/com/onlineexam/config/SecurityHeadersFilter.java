package com.onlineexam.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * Adds security headers to all HTTP responses to mitigate common web attacks.
 * Addresses: XSS, clickjacking, MIME-type sniffing, referrer leakage.
 */
@Component
public class SecurityHeadersFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (response instanceof HttpServletResponse httpResponse) {
      httpResponse.setHeader("X-Content-Type-Options", "nosniff");
      httpResponse.setHeader("X-Frame-Options", "DENY");
      httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
      httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
      // Disable proxy buffering for SSE streams (critical for AI streaming responses)
      httpResponse.setHeader("X-Accel-Buffering", "no");
      httpResponse.setHeader("Cache-Control", "no-cache");
      httpResponse.setHeader("Content-Security-Policy",
          "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'");
    }
    chain.doFilter(request, response);
  }
}
