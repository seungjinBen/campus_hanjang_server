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

    // ── 2026 가을 시즌 — TODO: 기획 확인 필요 — 가을축제 일정 확정 시 실제 오픈/종료일로 변경
    // 유저 정보 수집 시작일 — 가을 시즌 개발/테스트 시작
    private static final LocalDate USER_COLLECTION_START = LocalDate.of(2026, 8, 26);
    // 매칭 기능 오픈일 — 날짜 변경은 코드 수정 + 재배포가 유일한 방법 (클라이언트 조작 불가)
    private static final LocalDate MATCHING_FEATURE_START = LocalDate.of(2026, 8, 26);
    // 매칭 서비스 종료 시각 — 가을축제 종료일 확정 전까지 연말로 임시 설정
    private static final LocalDateTime MATCHING_TERMINATED_AT = LocalDateTime.of(2026, 12, 31, 0, 0, 0);

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
