package com.campushanjang.domain.match.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class MatchCardResponseDto {

    private final UUID candidateId;
    private final String nickname;
    private final String photoUrl;
    private final List<TraitDto> visibleTraits;
    private final double matchScore;

    @Getter
    @Builder
    public static class TraitDto {
        private final String traitKey;
        private final String traitValue;
    }
}
