package com.campushanjang.domain.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class TraitsUpdateRequestDto {

    @NotNull
    @Valid
    private List<TraitRequestDto> traits;
}
