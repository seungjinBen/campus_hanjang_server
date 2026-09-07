package com.campushanjang.domain.referral.dto;

import lombok.Builder;
import lombok.Getter;

// 초대 링크 미리보기(OG 이미지용) — 비인증 접근 허용이므로 닉네임 외 어떤 정보도 담지 않는다
@Getter
@Builder
public class ReferralPreviewResponseDto {

    private final String nickname;   // 코드가 유효하지 않으면 null
}
