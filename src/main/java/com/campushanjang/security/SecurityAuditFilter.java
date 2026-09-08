package com.campushanjang.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Order(2)
@Slf4j
public class SecurityAuditFilter extends OncePerRequestFilter {

    private static final List<String> SENSITIVE_PATHS = List.of(
            "/api/users/", "/api/match/received", "/api/match/select"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isSensitive = SENSITIVE_PATHS.stream().anyMatch(path::startsWith);
        if (isSensitive) {
            log.info("민감 경로 접근 ip={} method={} path={} ua={}",
                    getClientIp(request), request.getMethod(), path,
                    sanitizeForLog(request.getHeader("User-Agent")));
        }
        filterChain.doFilter(request, response);
    }

    // X-Forwarded-For는 조작 가능하므로 참고용으로만 사용
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? sanitizeForLog(forwarded.split(",")[0].trim()) : request.getRemoteAddr();
    }

    // 공격자 제어 값의 CRLF 로그 인젝션 방지
    private String sanitizeForLog(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[\\r\\n]", "_");
        return cleaned.length() > 200 ? cleaned.substring(0, 200) + "..." : cleaned;
    }
}
