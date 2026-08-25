package com.campushanjang.domain.verification.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// 학과 동적 사전 — 학년별 개편으로 과 이름이 바뀌므로 고정 목록 대신 인증 승인 시 자동 등록
@Entity
@Table(name = "departments")
@Getter
@NoArgsConstructor
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Department(String name) {
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }
}
