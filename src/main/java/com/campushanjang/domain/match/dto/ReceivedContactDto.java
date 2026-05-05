package com.campushanjang.domain.match.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ReceivedContactDto {

    private final UUID selectorId;
    private final String nickname;
    private final String photoUrl;
    private final List<MatchCardResponseDto.TraitDto> visibleTraits;
    private final LocalDateTime selectedAt;
}
