package com.campushanjang.domain.verification.entity.enums;

// 학생인증 진입 경로 — 승인 시 자동 채워지는 항목 범위와 에이전트 프롬프트/게이트가 이 값에 따라 갈린다
public enum VerificationMethod {
    SEJONG_QR,          // 세종대 모바일 앱 My QR 캡처 — 대학명·이름·학번·학과·생년월일 5개 항목 추출
    EVERYTIME_PROFILE   // 에브리타임 앱 '내 정보' 캡처 — 대학명·이름·학번만 추출 (안드로이드 QR 캡처 불가 대응)
}
