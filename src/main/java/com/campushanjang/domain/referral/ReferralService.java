package com.campushanjang.domain.referral;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.domain.referral.dto.ReferralMeResponseDto;
import com.campushanjang.domain.referral.dto.ReferralPreviewResponseDto;
import com.campushanjang.domain.user.UserRepository;
import com.campushanjang.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralService {

    // 혼동 문자(0/O, 1/I/L) 제외 — 구두 공유·수기 입력 대비
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_CODE_RETRY = 5;

    private final UserRepository userRepository;
    private final ReferralEventRepository referralEventRepository;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public ReferralMeResponseDto getMyReferral(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        // 초대 코드 lazy 생성 — 첫 공유 요청 시에만
        if (user.getReferralCode() == null) {
            user.assignReferralCode(generateUniqueCode());
            log.info("리퍼럴 코드 생성 userId={}", userId);
        }

        LocalDate today = LocalDate.now();
        boolean bonusActiveToday = referralEventRepository.hasRewardBetween(
                userId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());

        return ReferralMeResponseDto.builder()
                .code(user.getReferralCode())
                .bonusActiveToday(bonusActiveToday)
                .build();
    }

    // 초대 링크 미리보기(OG 이미지용) — 닉네임만 노출 (카드에 이미 공개되는 정보)
    @Transactional(readOnly = true)
    public ReferralPreviewResponseDto preview(String code) {
        String nickname = userRepository.findByReferralCode(code.trim().toUpperCase())
                .map(User::getNickname)
                .orElse(null);
        return ReferralPreviewResponseDto.builder().nickname(nickname).build();
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_RETRY; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!userRepository.existsByReferralCode(code)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}
