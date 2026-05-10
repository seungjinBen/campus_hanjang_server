package com.campushanjang.common.feature;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class FeatureToggleService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 유저 정보 수집 시작일 — 서비스 출시일
    private static final LocalDate USER_COLLECTION_START = LocalDate.of(2026, 5, 4);
    // 매칭 기능 오픈일 — 날짜 변경은 코드 수정 + 재배포가 유일한 방법 (클라이언트 조작 불가)
    private static final LocalDate MATCHING_FEATURE_START = LocalDate.of(2026, 5, 19);

    /**
     * 매칭 기능 활성화 여부 — 오직 서버 시스템 시간(Asia/Seoul) 기준으로 판단한다.
     * 클라이언트 요청의 어떤 값도 이 판단에 영향을 줄 수 없다.
     */
    public boolean isMatchingEnabled() {
        return !LocalDate.now(SEOUL).isBefore(MATCHING_FEATURE_START);
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
