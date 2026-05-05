package com.campushanjang.domain.match.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class SelectRequestDto {

    @NotNull
    private UUID candidateId;
}
