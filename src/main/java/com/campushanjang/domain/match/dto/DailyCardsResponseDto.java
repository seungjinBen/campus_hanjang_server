package com.campushanjang.domain.match.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DailyCardsResponseDto {

    private final List<MatchCardResponseDto> cards;
    private final int remainingSelectCount;
}
