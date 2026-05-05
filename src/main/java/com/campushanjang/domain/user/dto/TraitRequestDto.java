package com.campushanjang.domain.user.dto;

import com.campushanjang.domain.user.entity.enums.TraitKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TraitRequestDto {

    @NotNull
    private TraitKey traitKey;

    @NotBlank
    private String traitValue;

    private boolean isVisible = true;
}
