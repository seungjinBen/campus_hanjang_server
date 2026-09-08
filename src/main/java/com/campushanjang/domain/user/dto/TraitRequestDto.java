package com.campushanjang.domain.user.dto;

import com.campushanjang.domain.user.entity.enums.TraitKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TraitRequestDto {

    @NotNull
    private TraitKey traitKey;

    @NotBlank
    @Size(max = 100, message = "특징 값은 100자 이내여야 해요")
    private String traitValue;

    private boolean isVisible = true;
}
