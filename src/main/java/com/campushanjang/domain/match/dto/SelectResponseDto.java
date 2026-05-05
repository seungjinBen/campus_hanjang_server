package com.campushanjang.domain.match.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class SelectResponseDto {

    private final String type; // "CONTACT_REVEALED" or "NOTE_REQUIRED"
    private final String message;
    private final UUID selectedId;
    // 아래 두 필드는 CONTACT_REVEALED 시에만 채워짐
    private final String contactType;
    private final String contactValue;
}
