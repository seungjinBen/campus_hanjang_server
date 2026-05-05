package com.campushanjang.domain.match.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ReceivedNoteDto {

    private final UUID noteId;
    private final String status;
    private final String noteContent;
    private final LocalDateTime sentAt;
    private final LocalDateTime respondedAt;

    // PENDING 상태에서는 null — 수락 전까지 발신자 익명 보호
    private final UUID selectorId;
    private final String nickname;
    private final String photoUrl;
    private final List<MatchCardResponseDto.TraitDto> visibleTraits;
    private final String selectorContactType;
    private final String selectorContactValue;
}
