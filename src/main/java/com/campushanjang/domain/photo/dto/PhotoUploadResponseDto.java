package com.campushanjang.domain.photo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PhotoUploadResponseDto {

    private final String photoUrl;
    private final String thumbnailUrl;
}
