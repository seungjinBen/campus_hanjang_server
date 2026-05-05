package com.campushanjang.domain.match.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class SentNoteDto {

    private final UUID noteId;
    private final UUID selectedId;
    private final String noteContent;
    private final String status;
    private final LocalDateTime sentAt;
    private final LocalDateTime respondedAt;

    // 수락 시에만 상대방 연락처 포함 — 양방향 연락처 공개 규칙
    private final String selectedContactType;
    private final String selectedContactValue;
}
