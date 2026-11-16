package dev.julco.kitchen.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * MCP spec: servers must validate the Origin header, to stop a malicious web page from talking to
 * the MCP endpoint through the visitor's browser (DNS rebinding). AI clients call from their own
 * servers and send no Origin: they are not affected.
 */
final class OriginValidationFilter extends OncePerRequestFilter {

    private final Set<String> allowedOrigins;

    OriginValidationFilter(Set<String> allowedOrigins) {
        this.allowedOrigins = Set.copyOf(allowedOrigins);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !allowedOrigins.contains(origin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
