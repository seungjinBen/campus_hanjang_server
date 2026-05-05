package com.campushanjang.domain.match.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class NoteRespondResponseDto {

    private final String action;
    private final String message;

    // 수락 시에만 발신자 정보 및 연락처 포함
    private final UUID selectorId;
    private final String nickname;
    private final String photoUrl;
    private final List<MatchCardResponseDto.TraitDto> visibleTraits;
    private final String selectorContactType;
    private final String selectorContactValue;
}
