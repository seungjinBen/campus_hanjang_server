package com.campushanjang.config;

import com.campushanjang.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig rateLimitConfig;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String ip = getClientIp(request);
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 글로벌 IP 제한
        Bucket globalBucket = rateLimitConfig.getIpBucket(ip);
        if (!globalBucket.tryConsume(1)) {
            rejectRequest(response);
            return;
        }

        // 카카오 콜백 IP 제한
        if ("GET".equals(method) && uri.contains("/api/auth/kakao/callback")) {
            Bucket bucket = rateLimitConfig.getKakaoCallbackBucket(ip);
            if (!bucket.tryConsume(1)) {
                rejectRequest(response);
                return;
            }
        }

        // 개발용 로컬 로그인 IP 제한 — 실서비스 전 삭제 예정
        if ("POST".equals(method) && uri.equals("/api/auth/local/login")) {
            Bucket bucket = rateLimitConfig.getLocalLoginBucket(ip);
            if (!bucket.tryConsume(1)) {
                rejectRequest(response);
                return;
            }
        }

        // 서비스 상태 API IP 제한 (인증 불필요 엔드포인트)
        if ("GET".equals(method) && uri.equals("/api/service/status")) {
            Bucket bucket = rateLimitConfig.getServiceStatusBucket(ip);
            if (!bucket.tryConsume(1)) {
                rejectRequest(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        // XFF는 같은 호스트의 리버스 프록시(Caddy)를 거친 요청에서만 신뢰한다.
        // 프록시는 실제 접속 IP를 목록 끝에 append하므로 마지막 요소만 사용 —
        // 앞쪽 요소는 클라이언트가 임의 삽입 가능해 rate limit 우회에 악용된다.
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && isTrustedProxy(remoteAddr)) {
            String[] parts = xForwardedFor.split(",");
            return parts[parts.length - 1].trim();
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String addr) {
        return "127.0.0.1".equals(addr) || "::1".equals(addr) || "0:0:0:0:0:0:0:1".equals(addr);
    }

    private void rejectRequest(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
                "success", false,
                "error", Map.of(
                        "code", ErrorCode.RATE_LIMIT_EXCEEDED.getCode(),
                        "message", ErrorCode.RATE_LIMIT_EXCEEDED.getMessage()
                )
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
