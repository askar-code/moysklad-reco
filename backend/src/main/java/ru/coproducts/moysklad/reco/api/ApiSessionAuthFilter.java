package ru.coproducts.moysklad.reco.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiSessionAuthFilter extends OncePerRequestFilter {

    public static final String ACCOUNT_ID_ATTR = "resolvedAccountId";

    private final ApiSessionTokenService tokenService;

    public ApiSessionAuthFilter(ApiSessionTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api")) {
            return true;
        }
        return path.startsWith("/api/context/resolve")
                || path.startsWith("/api/moysklad/vendor/1.0");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            unauthorized(response, "Missing API session token");
            return;
        }

        String token = authHeader.substring("Bearer ".length());
        String accountId = tokenService.verifyAndExtractAccountId(token).orElse(null);
        if (accountId == null) {
            unauthorized(response, "Invalid API session token");
            return;
        }

        request.setAttribute(ACCOUNT_ID_ATTR, accountId);
        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
