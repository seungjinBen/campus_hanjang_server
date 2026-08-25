package com.campushanjang.domain.user.dto;

import com.campushanjang.domain.user.entity.enums.DeptFilterMode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DeptFilterRequestDto {

    @NotNull
    private DeptFilterMode mode;
}
