package com.campushanjang.domain.user.entity.enums;

// SAME_ONLY는 유저 풀이 작을 때 카드가 거의 안 나와 제거됨 (2026-08 기획 확정)
public enum DeptFilterMode {
    ALL,           // 전체 (기본)
    EXCLUDE_SAME   // 같은 과 제외 — 아는 사람 피하기
}
