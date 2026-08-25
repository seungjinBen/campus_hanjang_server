package com.campushanjang.domain.verification.agent;

import com.campushanjang.domain.verification.DepartmentRepository;
import com.campushanjang.domain.verification.StudentVerificationRepository;
import com.campushanjang.domain.verification.entity.enums.VerificationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

// 에이전트 도구의 로컬 실행자 — LLM이 요청한 DB 조회를 대신 수행하고 JSON 결과를 돌려준다
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationToolExecutor {

    private static final Set<VerificationStatus> APPROVED_STATUSES =
            Set.of(VerificationStatus.AUTO_APPROVED, VerificationStatus.APPROVED);

    private final StudentVerificationRepository verificationRepository;
    private final DepartmentRepository departmentRepository;
    private final ObjectMapper objectMapper;

    public String execute(String toolName, JsonNode input) {
        try {
            return switch (toolName) {
                case "check_student_no" -> checkStudentNo(input.path("student_no").asText());
                case "search_department" -> searchDepartment();
                default -> "{\"error\":\"알 수 없는 도구\"}";
            };
        } catch (Exception e) {
            // 도구 실패가 에이전트 전체를 죽이지 않도록 에러를 결과로 반환 — LLM이 NEEDS_REVIEW로 폴백하게 유도
            log.warn("도구 실행 실패 tool={} error={}", toolName, e.getMessage());
            return "{\"error\":\"도구 실행 실패\"}";
        }
    }

    // 1학번 1계정 — 이미 승인된 학번이면 에이전트가 거절 사유를 생성
    private String checkStudentNo(String studentNo) throws Exception {
        boolean alreadyApproved = studentNo != null && !studentNo.isBlank()
                && verificationRepository.existsByExtractedStudentNoAndStatusIn(studentNo.trim(), APPROVED_STATUSES);
        return objectMapper.writeValueAsString(Map.of("already_approved", alreadyApproved));
    }

    // 학과 동적 사전 전체 반환 — 표기 변형('컴공과'='컴퓨터공학과') 판단은 LLM이 수행
    private String searchDepartment() throws Exception {
        List<String> names = departmentRepository.findAllNames();
        return objectMapper.writeValueAsString(Map.of("existing_departments", names));
    }
}
