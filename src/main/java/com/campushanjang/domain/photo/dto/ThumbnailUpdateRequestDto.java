package com.campushanjang.domain.photo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ThumbnailUpdateRequestDto {

    @NotBlank
    private String thumbnailUrl;
}
