package com.campushanjang.domain.verification.agent;

import com.campushanjang.domain.verification.ClaudeApiClient;
import com.campushanjang.domain.verification.entity.enums.VerificationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 학생인증 판정 에이전트.
 * 추출 → 도구 호출(학번 중복/학과 사전 조회) → 판정의 멀티턴 루프를 수행하고,
 * 자동 승인은 규칙 기반 최종 게이트를 한 번 더 통과해야 한다 (LLM 단독 승인 금지).
 * 어떤 실패 상황에서도 예외를 던지지 않고 NEEDS_REVIEW로 폴백한다 (fail-safe).
 */
@Slf4j
@Component
public class VerificationAgent {

    // 무한 루프·비용 폭주 방지
    private static final int MAX_TURNS = 5;

    private final ClaudeApiClient claudeApiClient;
    private final VerificationToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;
    private final String allowedUniversity;
    private final Pattern studentNoPattern;
    private final double autoApproveThreshold;

    public VerificationAgent(ClaudeApiClient claudeApiClient,
                             VerificationToolExecutor toolExecutor,
                             ObjectMapper objectMapper,
                             @Value("${verification.allowed-university}") String allowedUniversity,
                             @Value("${verification.student-no-pattern}") String studentNoPattern,
                             @Value("${verification.auto-approve-threshold}") double autoApproveThreshold) {
        this.claudeApiClient = claudeApiClient;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
        this.allowedUniversity = allowedUniversity;
        this.studentNoPattern = Pattern.compile(studentNoPattern);
        this.autoApproveThreshold = autoApproveThreshold;
    }

    public AgentDecision verify(byte[] imageBytes, String mediaType) {
        long start = System.currentTimeMillis();
        int llmCalls = 0;
        List<AgentAction> actions = new ArrayList<>();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(ClaudeApiClient.userMessage(List.of(
                ClaudeApiClient.imageBlock(mediaType, Base64.getEncoder().encodeToString(imageBytes)),
                ClaudeApiClient.textBlock("이 학생앱 캡처 화면을 분석해 학생인증을 판정하라.")
        )));

        try {
            for (int turn = 0; turn < MAX_TURNS; turn++) {
                JsonNode res = claudeApiClient.createMessage(systemPrompt(), messages, tools());
                llmCalls++;

                if ("tool_use".equals(res.path("stop_reason").asText())) {
                    messages.add(ClaudeApiClient.assistantMessageFrom(res));
                    List<Map<String, Object>> toolResults = new ArrayList<>();
                    for (JsonNode block : res.path("content")) {
                        if (!"tool_use".equals(block.path("type").asText())) continue;
                        String toolName = block.path("name").asText();
                        JsonNode input = block.path("input");
                        String result = toolExecutor.execute(toolName, input);
                        actions.add(new AgentAction(toolName, input.toString(), result));
                        toolResults.add(ClaudeApiClient.toolResultBlock(block.path("id").asText(), result));
                    }
                    messages.add(ClaudeApiClient.userMessage(toolResults));
                    continue;
                }

                return parseAndValidate(res, llmCalls, elapsed(start), actionsJson(actions));
            }

            log.warn("에이전트 최대 턴 초과 — 검수 큐 폴백 llmCalls={}", llmCalls);
            return fallback("에이전트 최대 턴 초과", llmCalls, elapsed(start), actionsJson(actions));
        } catch (Exception e) {
            log.error("에이전트 실행 실패 — 검수 큐 폴백 error={}", e.getMessage(), e);
            return fallback("에이전트 오류: " + e.getMessage(), llmCalls, elapsed(start), actionsJson(actions));
        }
    }

    private AgentDecision parseAndValidate(JsonNode res, int llmCalls, long ms, String actionsJson) throws Exception {
        JsonNode json = objectMapper.readTree(extractJson(extractText(res)));

        VerificationStatus status = parseStatus(json.path("status").asText());
        double confidence = Math.max(0.0, Math.min(1.0, json.path("confidence").asDouble(0.0)));
        JsonNode ex = json.path("extracted");
        String university = textOrNull(ex, "university");
        String name = textOrNull(ex, "name");
        String studentNo = textOrNull(ex, "student_no");
        String department = textOrNull(ex, "department");
        LocalDate birthDate = parseDate(textOrNull(ex, "birth_date"));
        String reason = json.path("reason").asText("");
        String userMessage = json.path("user_message").asText("");

        // 규칙 기반 최종 게이트 — LLM 판정만으로는 절대 자동 승인하지 않는다
        if (status == VerificationStatus.AUTO_APPROVED) {
            // 1차: 다섯 항목 전원 존재 검사 — 잘린 캡처로 일부 항목이 null이면 승인 불가 (재촬영 유도)
            List<String> missing = new ArrayList<>();
            if (university == null) missing.add("대학명");
            if (name == null) missing.add("이름");
            if (studentNo == null) missing.add("학번");
            if (department == null) missing.add("학과");
            if (birthDate == null) missing.add("생년월일");

            if (!missing.isEmpty()) {
                log.warn("자동 승인 게이트 강등 사유=필수 항목 누락 missing={}", missing);
                status = VerificationStatus.RETRY_REQUESTED;
                reason = "[게이트 강등: 필수 항목 누락 " + missing + "] " + reason;
                userMessage = String.join(", ", missing)
                        + " 항목이 화면에서 확인되지 않았어요. 모든 정보가 보이도록 전체 화면을 캡처해서 다시 올려주세요.";
            } else {
                // 2차: 값 유효성 검사
                String downgrade = null;
                if (!allowedUniversity.equals(university.trim())) {
                    downgrade = "대학명 불일치";
                } else if (!studentNoPattern.matcher(studentNo.trim()).matches()) {
                    downgrade = "학번 형식 위반";
                } else if (confidence < autoApproveThreshold) {
                    downgrade = "신뢰도 미달";
                }
                if (downgrade != null) {
                    log.warn("자동 승인 게이트 강등 사유={} confidence={}", downgrade, confidence);
                    status = VerificationStatus.NEEDS_REVIEW;
                    reason = "[게이트 강등: " + downgrade + "] " + reason;
                    userMessage = "확인이 조금 더 필요해요. 검수가 완료되면 알려드릴게요.";
                }
            }
        }

        return new AgentDecision(status, confidence, university, name, studentNo, department,
                birthDate, reason, userMessage, llmCalls, ms, actionsJson);
    }

    // 에이전트가 낼 수 있는 상태만 허용 — 그 외 값은 검수 큐로
    private VerificationStatus parseStatus(String raw) {
        try {
            VerificationStatus status = VerificationStatus.valueOf(raw.trim());
            return switch (status) {
                case AUTO_APPROVED, RETRY_REQUESTED, NEEDS_REVIEW, REJECTED -> status;
                default -> VerificationStatus.NEEDS_REVIEW;
            };
        } catch (Exception e) {
            return VerificationStatus.NEEDS_REVIEW;
        }
    }

    private AgentDecision fallback(String reason, int llmCalls, long ms, String actionsJson) {
        return new AgentDecision(VerificationStatus.NEEDS_REVIEW, 0.0,
                null, null, null, null, null,
                reason, "확인이 조금 더 필요해요. 검수가 완료되면 알려드릴게요.",
                llmCalls, ms, actionsJson);
    }

    private String extractText(JsonNode res) {
        for (JsonNode block : res.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                return block.path("text").asText();
            }
        }
        throw new IllegalStateException("응답에 text 블록 없음");
    }

    // LLM은 "JSON만 출력" 지시를 무시하고 잡담·코드 펜스를 붙일 수 있다 — 형식 지시를 믿지 말고 파서가 방어
    private String extractJson(String text) {
        String t = text.trim();
        // 1) 코드 펜스가 어디에 있든 그 안의 내용을 우선 추출
        int fenceStart = t.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = t.indexOf('\n', fenceStart);
            int fenceEnd = contentStart >= 0 ? t.indexOf("```", contentStart) : -1;
            if (contentStart >= 0 && fenceEnd > contentStart) {
                t = t.substring(contentStart + 1, fenceEnd).trim();
            }
        }
        // 2) 그래도 앞뒤에 잡담이 남아 있으면 첫 '{'부터 마지막 '}'까지 절단
        int braceStart = t.indexOf('{');
        int braceEnd = t.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return t.substring(braceStart, braceEnd + 1);
        }
        return t;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText().trim();
        return (s.isEmpty() || "null".equalsIgnoreCase(s)) ? null : s;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null) return null;
        try {
            return LocalDate.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private String actionsJson(List<AgentAction> actions) {
        try {
            return objectMapper.writeValueAsString(actions);
        } catch (Exception e) {
            return "[]";
        }
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private List<Map<String, Object>> tools() {
        return List.of(
                Map.of(
                        "name", "check_student_no",
                        "description", "학번이 이미 다른 계정으로 승인되었는지 조회한다. 학번을 판독했다면 반드시 호출하라.",
                        "input_schema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "student_no", Map.of("type", "string", "description", "판독한 8자리 학번")),
                                "required", List.of("student_no"))),
                Map.of(
                        "name", "search_department",
                        "description", "기존에 등록된 학과 이름 목록을 조회한다. 판독한 학과가 기존 학과의 표기 변형인지 판단하는 데 사용한다.",
                        "input_schema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "department_name", Map.of("type", "string", "description", "판독한 학과명")),
                                "required", List.of("department_name")))
        );
    }

    private String systemPrompt() {
        return """
                너는 대학 축제 매칭 서비스 '캠퍼스한장'의 학생인증 심사관이다.
                사용자가 업로드한 대학 학생앱의 모바일 신분증(QR) 화면 캡처를 분석해 인증을 판정한다.

                [허용 대학] %s만 허용한다. 다른 대학 화면은 REJECTED.
                단, 화면 표기에 영문 병기나 변형이 있어도('세종대학교(SEJONG UNIVERSITY)', 'SEJONG UNIV' 병기 등)
                허용 대학과 같은 대학이면 일치로 판정하고, university 필드는 정규화된 이름만 출력하라. (예: "세종대학교")
                [학번 형식] 8자리 숫자 (예: 24011234). 형식이 다르면 판독 오류인지 위조인지 검토하라.

                [추출 항목]
                - university: 화면의 대학명
                - name: 학생 이름
                - student_no: 학번
                - department: 학과명
                - birth_date: 생년월일 (YYYY-MM-DD)

                [추출 원칙]
                - 화면에 실제로 보이는 값만 추출하라. 보이지 않는 값을 학번이나 다른 항목에서 유추해 채우지 마라. 안 보이면 null.
                - 다섯 항목 중 하나라도 화면에서 확인할 수 없으면 절대 AUTO_APPROVED를 내리지 마라.
                  잘리거나 가려진 것이면 RETRY_REQUESTED로 재촬영을 요청하라.

                [위조 신호 검토]
                - 텍스트 정렬·폰트 불일치, 편집 흔적
                - 화면을 다른 기기로 재촬영한 사진 (모아레 패턴, 기울어짐, 기기 베젤)
                - 학생앱 UI가 아닌 화면 (웹페이지, 이미지 편집 앱 등)
                - QR 코드 부재

                [도구 사용 지침]
                - student_no를 판독했으면 반드시 check_student_no로 승인 이력을 확인하라. 이미 승인된 학번이면 REJECTED.
                - department를 판독했으면 반드시 search_department로 기존 학과 목록을 조회하라.
                  기존 목록에 같은 과(표기 변형 포함: '컴공과'='컴퓨터공학과')가 있으면 그 정식 명칭으로 department를 정규화하라.
                  없으면 판독한 이름 그대로 둔다.

                [판정 규칙]
                - AUTO_APPROVED: 다섯 항목(대학명·이름·학번·학과·생년월일) 모두 화면에서 확인됨 + 대학명 일치 + 학번 형식 유효 + 학번 미중복 + QR 존재 + 위조 신호 없음
                - RETRY_REQUESTED: 위조는 아니지만 흐림·잘림·빛반사·화면 절단 등으로 판독 불가 항목이 하나라도 있는 경우.
                  user_message에 무엇이 안 보였고 어떻게 다시 캡처할지 구체적으로 안내하라.
                - NEEDS_REVIEW: 판독은 됐지만 확신이 부족한 경우 (약한 위조 의심, 항목 일부 불일치)
                - REJECTED: 타 대학 화면, QR 없음, 명백한 위조 신호, 이미 승인된 학번, 학생증 화면이 아님

                [출력 형식]
                판정이 끝나면 도구 호출 없이 아래 JSON만 출력하라. 코드 펜스나 다른 텍스트를 붙이지 마라.
                {
                  "status": "AUTO_APPROVED | RETRY_REQUESTED | NEEDS_REVIEW | REJECTED",
                  "confidence": 0.0에서 1.0 사이 숫자,
                  "extracted": {
                    "university": "...", "name": "...", "student_no": "...",
                    "department": "...", "birth_date": "YYYY-MM-DD 또는 null"
                  },
                  "reason": "판단 근거 (관리자용, 한국어)",
                  "user_message": "사용자에게 보여줄 안내문 (한국어, 존댓말)"
                }
                """.formatted(allowedUniversity);
    }
}
