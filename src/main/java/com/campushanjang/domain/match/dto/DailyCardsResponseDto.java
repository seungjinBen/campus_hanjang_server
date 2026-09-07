package com.campushanjang.domain.match.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DailyCardsResponseDto {

    private final List<MatchCardResponseDto> cards;
    private final int remainingSelectCount;
    // 유저별 동적 한도 (기본 2 + 얼리버드 + 리퍼럴) — 프론트 "N/limit" 표시용
    private final int dailySelectLimit;
}
