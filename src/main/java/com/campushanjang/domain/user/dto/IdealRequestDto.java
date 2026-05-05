package com.campushanjang.domain.user.dto;

import com.campushanjang.domain.user.entity.enums.TraitKey;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IdealRequestDto {

    @NotNull
    private TraitKey traitKey;

    private String traitValue;
}
