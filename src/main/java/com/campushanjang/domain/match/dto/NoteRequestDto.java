package com.campushanjang.domain.match.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class NoteRequestDto {

    @NotNull
    private UUID selectedId;

    @NotBlank
    @Size(max = 50, message = "쪽지는 50자 이내로 작성해 주세요")
    private String content;
}
