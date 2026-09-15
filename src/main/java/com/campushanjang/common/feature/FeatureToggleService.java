package com.campushanjang.common.feature;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class FeatureToggleService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // ── 2026 가을 시즌 (세종대 축제 연동)
    private static final LocalDate USER_COLLECTION_START = LocalDate.of(2026, 8, 26);
    // 매칭 기능 오픈일 — 세종대 가을축제 첫날. 날짜 변경은 코드 수정 + 재배포만 가능 (클라이언트 조작 불가)
    private static final LocalDate MATCHING_FEATURE_START = LocalDate.of(2026, 9, 28);
    // 매칭 서비스 종료 시각 — 세종대 가을축제 마지막 날 자정
    private static final LocalDateTime MATCHING_TERMINATED_AT = LocalDateTime.of(2026, 10, 2, 23, 59, 59);

    // 얼리버드 사전등록 기간 — 이 기간 내 학생인증 승인 완료 시 운영기간 내내 하루 선택 +1 (CLAUDE.md 16-5)
    private static final LocalDate EARLY_BIRD_START = LocalDate.of(2026, 9, 21);
    private static final LocalDate EARLY_BIRD_END = LocalDate.of(2026, 9, 27);

    /**
     * 매칭 기능 활성화 여부 — 오직 서버 시스템 시간(Asia/Seoul) 기준으로 판단한다.
     * 클라이언트 요청의 어떤 값도 이 판단에 영향을 줄 수 없다.
     */
    public boolean isMatchingEnabled() {
        return !LocalDate.now(SEOUL).isBefore(MATCHING_FEATURE_START);
    }

    // 매칭 서비스가 종료됐는지 여부 (카드/선택/쪽지 전송 차단용)
    public boolean isMatchingTerminated() {
        return LocalDateTime.now(SEOUL).isAfter(MATCHING_TERMINATED_AT);
    }

    // 얼리버드 판정도 서버 시간(Asia/Seoul) 기준 — 클라이언트 값 개입 불가
    public boolean isEarlyBirdPeriod() {
        LocalDate today = LocalDate.now(SEOUL);
        return !today.isBefore(EARLY_BIRD_START) && !today.isAfter(EARLY_BIRD_END);
    }

    public boolean isUserCollectionEnabled() {
        return !LocalDate.now(SEOUL).isBefore(USER_COLLECTION_START);
    }

    public long daysUntilMatchingOpen() {
        LocalDate today = LocalDate.now(SEOUL);
        if (!today.isBefore(MATCHING_FEATURE_START)) return 0L;
        return ChronoUnit.DAYS.between(today, MATCHING_FEATURE_START);
    }

    public LocalDate getMatchingOpenDate() {
        return MATCHING_FEATURE_START;
    }
}
