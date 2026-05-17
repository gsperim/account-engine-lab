package br.com.carrefour.consolidado.adapter.in.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingContextFilter extends OncePerRequestFilter {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        try {
            MDC.put("http_method", request.getMethod());
            MDC.put("http_path",   sanitize(request.getRequestURI()));
            chain.doFilter(request, response);
        } finally {
            MDC.remove("http_method");
            MDC.remove("http_path");
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/health") || path.startsWith("/actuator");
    }

    private String sanitize(String uri) {
        return DATE_PATTERN.matcher(
                UUID_PATTERN.matcher(uri).replaceAll("{id}"))
                .replaceAll("{data}");
    }
}
