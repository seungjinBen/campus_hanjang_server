package com.campushanjang.domain.match.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NoteRespondRequestDto {

    @NotBlank
    @Pattern(regexp = "ACCEPTED|REJECTED", message = "action은 ACCEPTED 또는 REJECTED여야 해요")
    private String action;
}
