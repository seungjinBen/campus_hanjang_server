package com.campushanjang.domain.referral.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReferralMeResponseDto {

    private final String code;
    // 오늘 리퍼럴 보너스(+1) 활성 여부 — 초대한 친구가 오늘 인증 승인 완료
    private final boolean bonusActiveToday;
}
