package com.campushanjang.domain.auth;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.common.util.EncryptionUtil;
import com.campushanjang.domain.auth.dto.KakaoUserInfoDto;
import com.campushanjang.domain.auth.dto.LocalLoginRequestDto;
import com.campushanjang.domain.auth.entity.RefreshToken;
import com.campushanjang.domain.referral.ReferralEventRepository;
import com.campushanjang.domain.referral.entity.ReferralEvent;
import com.campushanjang.domain.user.UserRepository;
import com.campushanjang.domain.user.entity.User;
import com.campushanjang.security.JwtProvider;
import com.campushanjang.domain.user.entity.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final UserRepository userRepository;
    private final AuthRepository authRepository;
    private final WithdrawalBlocklistRepository withdrawalBlocklistRepository;
    private final ReferralEventRepository referralEventRepository;
    private final JwtProvider jwtProvider;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final Environment environment;

    @Value("${admin.kakao-id:}")
    private String adminKakaoId;

    // prod 테스트 계정 화이트리스트 — 미설정 시 로컬 로그인 자체가 비활성 (fail-safe)
    @Value("${local-login.allowed-emails:}")
    private String localLoginAllowedEmails;

    @Value("${verification.allowed-university}")
    private String allowedUniversity;

    public String getKakaoAuthorizationUrl(String state) {
        return kakaoOAuthClient.buildAuthorizationUrl(state);
    }

    @Transactional
    public KakaoLoginResult kakaoLogin(String code, String refCode) {
        String kakaoAccessToken = kakaoOAuthClient.getAccessToken(code);
        KakaoUserInfoDto kakaoUserInfo = kakaoOAuthClient.getUserInfo(kakaoAccessToken);
        String kakaoId = kakaoUserInfo.getKakaoId();

        boolean isNewUser = !userRepository.findByKakaoId(kakaoId).isPresent();

        if (isNewUser && withdrawalBlocklistRepository
                .existsByKakaoIdHashAndReregistrationAllowedAtAfter(
                        EncryptionUtil.hmacSha256(kakaoId), LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.WITHDRAWAL_REREGISTRATION_BLOCKED);
        }

        User user = userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .kakaoId(kakaoId)
                            .build();
                    return userRepository.save(newUser);
                });

        user.updateLastLoginAt();

        // 리퍼럴 가입 기록 — 신규 가입 시에만, PENDING 상태로 (보상은 학생인증 승인 시 확정)
        if (isNewUser) {
            applyReferral(user, refCode);
        }

        // ADMIN_KAKAO_ID 환경변수에 등록된 카카오 ID면 관리자 역할 부여
        if (!adminKakaoId.isBlank() && adminKakaoId.equals(kakaoUserInfo.getKakaoId())
                && user.getRole() != UserRole.ADMIN) {
            user.assignAdminRole();
            log.info("관리자 역할 부여 userId={}", user.getId());
        }

        log.info("카카오 로그인 userId={} isNewUser={} role={}", user.getId(), isNewUser, user.getRole());

        String genderStr = user.getGender() != null ? user.getGender().name() : null;
        String roleStr = user.getRole().name();
        String accessToken = jwtProvider.issueAccessToken(user.getId().toString(), genderStr, roleStr);
        String rawRefreshToken = jwtProvider.issueRefreshToken(user.getId().toString());

        saveRefreshToken(user, rawRefreshToken);

        return new KakaoLoginResult(accessToken, rawRefreshToken, isNewUser, roleStr);
    }

    public record KakaoLoginResult(String accessToken, String refreshToken, boolean isNewUser, String role) {}

    // 초대 코드로 초대자 조회 → referred_by 기록 + PENDING 이벤트 생성. 실패는 가입을 막지 않는다 (fail-safe)
    private void applyReferral(User newUser, String refCode) {
        if (refCode == null || refCode.isBlank()) return;
        userRepository.findByReferralCode(refCode.trim().toUpperCase())
                .filter(referrer -> !referrer.getId().equals(newUser.getId())) // 자기 초대 금지
                .ifPresent(referrer -> {
                    // referee UNIQUE의 애플리케이션 선검사 — 동시성은 DB 제약이 최종 방어
                    if (referralEventRepository.existsByRefereeId(newUser.getId())) return;
                    newUser.applyReferredBy(referrer.getId());
                    referralEventRepository.save(ReferralEvent.builder()
                            .referrer(referrer)
                            .referee(newUser)
                            .build());
                    log.info("리퍼럴 가입 기록 referrerId={} refereeId={}", referrer.getId(), newUser.getId());
                });
    }

    // 테스트 계정 로그인 — dev는 자유, prod는 LOCAL_LOGIN_ALLOWED_EMAILS 화이트리스트만
    @Transactional
    public KakaoLoginResult localLogin(LocalLoginRequestDto request) {
        String email = request.getEmail();
        validateLocalLoginAllowed(email);

        User user = userRepository.findByEmail(email).orElse(null);
        boolean isNewUser = (user == null);

        if (isNewUser) {
            user = userRepository.save(User.builder()
                    .kakaoId("local:" + UUID.randomUUID())
                    .email(email)
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .build());
            // 테스트 계정은 LLM 인증 없이 관리자 생성에 준하는 경로로 인증 처리.
            // birthDate 입력 경로는 applyStudentVerification과 관리자 생성뿐이라는 불변식을 유지한다 (CLAUDE.md §5)
            if (request.getBirthDate() != null && request.getDepartment() != null
                    && !request.getDepartment().isBlank()) {
                user.applyStudentVerification(
                        allowedUniversity, request.getDepartment().trim(), request.getBirthDate());
            }
            applyReferral(user, request.getRefCode());
            log.info("로컬 계정 생성 userId={} verified={}", user.getId(), user.isStudentVerified());
        } else {
            if (user.getPasswordHash() == null
                    || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
        }

        user.updateLastLoginAt();

        log.info("로컬 로그인 userId={} isNewUser={}", user.getId(), isNewUser);

        String genderStr = user.getGender() != null ? user.getGender().name() : null;
        String roleStr = user.getRole().name();
        String accessToken = jwtProvider.issueAccessToken(user.getId().toString(), genderStr, roleStr);
        String rawRefreshToken = jwtProvider.issueRefreshToken(user.getId().toString());

        saveRefreshToken(user, rawRefreshToken);

        return new KakaoLoginResult(accessToken, rawRefreshToken, isNewUser, roleStr);
    }

    // dev: 화이트리스트 검사 생략 (로컬 개발 편의). prod: 미설정=전면 차단, 미등록 이메일=거부.
    // 두 경우 모두 동일한 INVALID_CREDENTIALS — 기능 존재 여부를 외부에 노출하지 않는다
    private void validateLocalLoginAllowed(String email) {
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            return;
        }
        boolean permitted = Arrays.stream(localLoginAllowedEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(allowed -> allowed.equalsIgnoreCase(email));
        if (!permitted) {
            log.warn("로컬 로그인 화이트리스트 외 시도");
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken) {
        jwtProvider.validateToken(rawRefreshToken);
        if (!"refresh".equals(jwtProvider.getTokenType(rawRefreshToken))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(jwtProvider.getUserId(rawRefreshToken));

        // revoked 포함 전체 조회 — 폐기된 토큰 재사용을 탈취 신호로 감지하기 위함
        String compressedToken = compressForBcrypt(rawRefreshToken);
        RefreshToken matched = authRepository.findByUserId(userId).stream()
                .filter(t -> passwordEncoder.matches(compressedToken, t.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (matched.isRevoked()) {
            // 회전으로 폐기된 토큰이 다시 사용됨 = 토큰 탈취 가능성 — 전체 세션 차단 (fail-safe)
            authRepository.revokeAllByUserId(userId);
            log.warn("폐기된 refresh token 재사용 감지 — 전체 세션 폐기 userId={}", userId);
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (matched.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        // 회전 — 사용된 refresh token은 즉시 폐기하고 새 토큰 발급 (CLAUDE.md §6)
        matched.revoke();
        String newRefreshToken = jwtProvider.issueRefreshToken(userId.toString());
        saveRefreshToken(user, newRefreshToken);

        String genderStr = user.getGender() != null ? user.getGender().name() : null;
        String roleStr = user.getRole().name();
        String newAccessToken = jwtProvider.issueAccessToken(userId.toString(), genderStr, roleStr);
        return new RefreshResult(newAccessToken, roleStr, newRefreshToken);
    }

    public record RefreshResult(String accessToken, String role, String refreshToken) {}

    @Transactional
    public void logout(UUID userId) {
        authRepository.revokeAllByUserId(userId);
        log.info("로그아웃 userId={}", userId);
    }

    private void saveRefreshToken(User user, String rawToken) {
        String tokenHash = passwordEncoder.encode(compressForBcrypt(rawToken));
        long expirationMs = jwtProvider.getRefreshExpiration();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expirationMs / 1000);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();
        authRepository.save(refreshToken);
    }

    // bcrypt는 72바이트 제한 — JWT(200바이트 이상)는 SHA-256(base64, 44자)으로 압축 후 해싱한다.
    // Spring Security 6.5(Boot 3.5)부터 초과 입력이 조용한 절단 대신 예외를 던진다.
    private String compressForBcrypt(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원", e);
        }
    }
}
