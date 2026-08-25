package com.campushanjang.domain.verification;

import com.campushanjang.domain.verification.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    boolean existsByName(String name);

    Optional<Department> findByName(String name);

    // 에이전트 도구: 기존 학과 사전 전체 — LLM이 유사 학과 여부를 판단할 근거
    @Query("SELECT d.name FROM Department d ORDER BY d.name")
    List<String> findAllNames();
}
