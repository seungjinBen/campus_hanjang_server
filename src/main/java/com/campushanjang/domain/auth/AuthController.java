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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private static final String OAUTH_STATE_COOKIE = "kakao_oauth_state";

    @Value("${app.secure-cookie:true}")
    private boolean secureCookie;

    @GetMapping("/kakao")
    public ResponseEntity<Void> kakaoRedirect(HttpServletResponse response) {
        // 로그인 CSRF 방어 — state를 쿠키에 심고 카카오 인가 URL에도 실어, 콜백에서 대조한다
        String state = generateState();
        ResponseCookie stateCookie = ResponseCookie.from(OAUTH_STATE_COOKIE, state)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/api/auth")
                .maxAge(Duration.ofMinutes(10))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie.toString());
        return ResponseEntity.status(302)
                .location(URI.create(authService.getKakaoAuthorizationUrl(state)))
                .build();
    }

    @GetMapping("/kakao/callback")
    public ResponseEntity<ApiResponse<TokenResponseDto>> kakaoCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String ref,   // 초대 코드 — 프론트가 localStorage에서 첨부
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        validateOAuthState(request, state);
        clearCookie(response, OAUTH_STATE_COOKIE, "/api/auth");

        AuthService.KakaoLoginResult result = authService.kakaoLogin(code, ref);
        setRefreshTokenCookie(response, result.refreshToken());

        TokenResponseDto body = TokenResponseDto.builder()
                .accessToken(result.accessToken())
                .isNewUser(result.isNewUser())
                .role(result.role())
                .build();
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    // 테스트 계정 로그인 — dev는 자유, prod는 LOCAL_LOGIN_ALLOWED_EMAILS 화이트리스트만 (미설정 시 비활성)
    @PostMapping("/local/login")
    public ResponseEntity<ApiResponse<TokenResponseDto>> localLogin(
            @RequestBody @Valid LocalLoginRequestDto request,
            HttpServletResponse response
    ) {
        AuthService.KakaoLoginResult result = authService.localLogin(request);
        setRefreshTokenCookie(response, result.refreshToken());

        TokenResponseDto body = TokenResponseDto.builder()
                .accessToken(result.accessToken())
                .isNewUser(result.isNewUser())
                .role(result.role())
                .build();
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponseDto>> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String rawRefreshToken = extractRefreshTokenFromCookie(request);
        AuthService.RefreshResult result = authService.refresh(rawRefreshToken);
        // 회전된 새 refresh token으로 쿠키 교체
        setRefreshTokenCookie(response, result.refreshToken());

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
        clearCookie(response, "refreshToken", "/");
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // Refresh Token — HttpOnly + Secure(prod only) + SameSite=Strict 쿠키.
    // setHeader 방식은 응답에 쿠키가 2개 이상이면 유실되므로 ResponseCookie로 헤더를 개별 추가한다.
    private void setRefreshTokenCookie(HttpServletResponse response, String rawRefreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", rawRefreshToken)
                .httpOnly(true)
                .secure(secureCookie) // dev: false (HTTP localhost), prod: true
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name, String path) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path(path)
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validateOAuthState(HttpServletRequest request, String state) {
        String cookieState = request.getCookies() == null ? null
                : Arrays.stream(request.getCookies())
                        .filter(c -> OAUTH_STATE_COOKIE.equals(c.getName()))
                        .findFirst()
                        .map(Cookie::getValue)
                        .orElse(null);
        if (state == null || cookieState == null
                || !MessageDigest.isEqual(
                        state.getBytes(StandardCharsets.UTF_8),
                        cookieState.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
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
