package com.campushanjang.domain.auth;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.domain.auth.dto.KakaoUserInfoDto;
import com.campushanjang.domain.auth.dto.TokenResponseDto;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Value("${admin.kakao-id:}")
    private String adminKakaoId;

    public String getKakaoAuthorizationUrl() {
        return kakaoOAuthClient.buildAuthorizationUrl();
    }

    @Transactional
    public KakaoLoginResult kakaoLogin(String code, String refCode) {
        String kakaoAccessToken = kakaoOAuthClient.getAccessToken(code);
        KakaoUserInfoDto kakaoUserInfo = kakaoOAuthClient.getUserInfo(kakaoAccessToken);
        String kakaoId = kakaoUserInfo.getKakaoId();

        boolean isNewUser = !userRepository.findByKakaoId(kakaoId).isPresent();

        if (isNewUser && withdrawalBlocklistRepository
                .existsByKakaoIdAndReregistrationAllowedAtAfter(kakaoId, LocalDateTime.now())) {
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

    // 개발용 로컬 로그인 — 실서비스 전 삭제 예정
    @Transactional
    public KakaoLoginResult localLogin(String email, String password, String refCode) {
        User user = userRepository.findByEmail(email).orElse(null);
        boolean isNewUser = (user == null);

        if (isNewUser) {
            user = userRepository.save(User.builder()
                    .kakaoId("local:" + UUID.randomUUID())
                    .email(email)
                    .passwordHash(passwordEncoder.encode(password))
                    .build());
            applyReferral(user, refCode);
            log.info("로컬 계정 생성 userId={}", user.getId());
        } else {
            if (user.getPasswordHash() == null
                    || !passwordEncoder.matches(password, user.getPasswordHash())) {
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

    @Transactional
    public RefreshResult refresh(String rawRefreshToken) {
        jwtProvider.validateToken(rawRefreshToken);
        String userId = jwtProvider.getUserId(rawRefreshToken);

        List<RefreshToken> tokens = authRepository.findByUserIdAndIsRevokedFalse(UUID.fromString(userId));
        boolean valid = tokens.stream()
                .anyMatch(t -> passwordEncoder.matches(rawRefreshToken, t.getTokenHash())
                        && t.getExpiresAt().isAfter(LocalDateTime.now()));

        if (!valid) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        String genderStr = user.getGender() != null ? user.getGender().name() : null;
        String roleStr = user.getRole().name();
        String newAccessToken = jwtProvider.issueAccessToken(userId, genderStr, roleStr);
        return new RefreshResult(newAccessToken, roleStr);
    }

    public record RefreshResult(String accessToken, String role) {}

    @Transactional
    public void logout(UUID userId) {
        authRepository.revokeAllByUserId(userId);
        log.info("로그아웃 userId={}", userId);
    }

    private void saveRefreshToken(User user, String rawToken) {
        String tokenHash = passwordEncoder.encode(rawToken);
        long expirationMs = jwtProvider.getRefreshExpiration();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expirationMs / 1000);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();
        authRepository.save(refreshToken);
    }
}
