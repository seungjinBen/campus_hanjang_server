package com.campushanjang.security;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.domain.user.entity.enums.Gender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    // 탈퇴 계정 보호는 refresh token revoke 로 충분하다.
    // 요청마다 DB 조회 시 100명 동시 접속에서 HikariCP 포화 → 타임아웃 발생 확인됨 (성능 테스트 2026-05-04)
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                jwtProvider.validateToken(token);
                // refresh 토큰(30일)을 Bearer 헤더로 access처럼 쓰는 것 차단
                if (!"access".equals(jwtProvider.getTokenType(token))) {
                    log.warn("access 타입이 아닌 JWT 인증 시도 차단");
                    filterChain.doFilter(request, response);
                    return;
                }
                String userId = jwtProvider.getUserId(token);
                String genderStr = jwtProvider.getGender(token);
                Gender gender = genderStr != null ? Gender.valueOf(genderStr) : null;
                String roleStr = jwtProvider.getRole(token);

                UserPrincipal principal = new UserPrincipal(UUID.fromString(userId), gender, roleStr);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException e) {
                log.warn("JWT 인증 실패 error={}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
