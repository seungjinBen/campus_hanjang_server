package com.campushanjang.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class DateManipulationFilter extends OncePerRequestFilter {

    // 날짜 조작에 자주 사용되는 헤더 목록
    private static final List<String> SUSPICIOUS_DATE_HEADERS = List.of(
            "X-Date", "X-Custom-Date", "X-Server-Date", "X-Override-Date",
            "X-Forwarded-Date", "X-Real-Date"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        SUSPICIOUS_DATE_HEADERS.forEach(header -> {
            String value = request.getHeader(header);
            if (value != null) {
                log.warn("날짜 조작 의심 헤더 감지 header={} value={} ip={}",
                        header, value, request.getRemoteAddr());
            }
        });
        filterChain.doFilter(request, response);
    }
}
