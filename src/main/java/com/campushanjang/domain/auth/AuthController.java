package com.campushanjang.domain.auth;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.common.response.ApiResponse;
import com.campushanjang.domain.auth.dto.LocalLoginRequestDto;
import com.campushanjang.domain.auth.dto.TokenResponseDto;
import com.campushanjang.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.secure-cookie:true}")
    private boolean secureCookie;

    @GetMapping("/kakao")
    public ResponseEntity<Void> kakaoRedirect() {
        return ResponseEntity.status(302)
                .location(URI.create(authService.getKakaoAuthorizationUrl()))
                .build();
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<ApiResponse<TokenResponseDto>> kakaoCallback(
            @RequestParam String code,
            HttpServletResponse response
    ) {
        AuthService.KakaoLoginResult result = authService.kakaoLogin(code);
        setRefreshTokenCookie(response, result.refreshToken());

        TokenResponseDto body = TokenResponseDto.builder()
                .accessToken(result.accessToken())
                .isNewUser(result.isNewUser())
                .role(result.role())
                .build();
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    // 개발용 로컬 로그인 — 실서비스 전 삭제 예정
    @PostMapping("/local/login")
    public ResponseEntity<ApiResponse<TokenResponseDto>> localLogin(
            @RequestBody @Valid LocalLoginRequestDto request,
            HttpServletResponse response
    ) {
        AuthService.KakaoLoginResult result = authService.localLogin(request.getEmail(), request.getPassword());
        setRefreshTokenCookie(response, result.refreshToken());

        TokenResponseDto body = TokenResponseDto.builder()
                .accessToken(result.accessToken())
                .isNewUser(result.isNewUser())
                .role(result.role())
                .build();
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponseDto>> refresh(HttpServletRequest request) {
        String rawRefreshToken = extractRefreshTokenFromCookie(request);
        AuthService.RefreshResult result = authService.refresh(rawRefreshToken);

        TokenResponseDto body = TokenResponseDto.builder()
                .accessToken(result.accessToken())
                .isNewUser(false)
                .role(result.role())
                .build();
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletResponse response
    ) {
        authService.logout(principal.getId());

        Cookie expiredCookie = new Cookie("refreshToken", "");
        expiredCookie.setMaxAge(0);
        expiredCookie.setPath("/");
        expiredCookie.setHttpOnly(true);
        response.addCookie(expiredCookie);

        return ResponseEntity.ok(ApiResponse.ok());
    }

    // Refresh Token — HttpOnly + Secure(prod only) + SameSite=Strict 쿠키
    private void setRefreshTokenCookie(HttpServletResponse response, String rawRefreshToken) {
        Cookie refreshCookie = new Cookie("refreshToken", rawRefreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(secureCookie); // dev: false (HTTP localhost), prod: true
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(30 * 24 * 60 * 60);
        response.addCookie(refreshCookie);
        String existingCookieHeader = response.getHeader("Set-Cookie");
        if (existingCookieHeader != null) {
            response.setHeader("Set-Cookie", existingCookieHeader + "; SameSite=Strict");
        }
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> "refreshToken".equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
