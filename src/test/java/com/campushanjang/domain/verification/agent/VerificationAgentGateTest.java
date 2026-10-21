package com.campushanjang.domain.verification.agent;

import com.campushanjang.domain.verification.ClaudeApiClient;
import com.campushanjang.domain.verification.entity.enums.VerificationMethod;
import com.campushanjang.domain.verification.entity.enums.VerificationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 학생인증 에이전트의 규칙 기반 게이트·응답 파서·멀티턴 루프 골든 테스트.
 *
 * <p>Claude API 응답을 손으로 만든 JSON으로 대체해 LLM 호출 없이 검증한다.
 * 핵심 불변 규칙: LLM이 AUTO_APPROVED를 내더라도 코드 게이트를 통과하지 못하면 자동 승인되지 않는다.
 */
class VerificationAgentGateTest {

    private static final String ALLOWED_UNIVERSITY = "세종대학교";
    private static final double THRESHOLD = 0.85;
    private static final byte[] IMAGE = new byte[]{1, 2, 3};

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ClaudeApiClient claudeApiClient;
    private VerificationToolExecutor toolExecutor;
    private VerificationAgent agent;

    @BeforeEach
    void setUp() {
        claudeApiClient = mock(ClaudeApiClient.class);
        toolExecutor = mock(VerificationToolExecutor.class);
        agent = new VerificationAgent(claudeApiClient, toolExecutor, objectMapper,
                ALLOWED_UNIVERSITY, "^\\d{8}$", "^\\d{2}학번$", THRESHOLD);
    }

    // ── 응답 빌더 ──

    /** stop_reason=end_turn, text 블록 하나짜리 최종 응답 */
    private JsonNode finalResponse(String text) throws Exception {
        return objectMapper.valueToTree(Map.of(
                "stop_reason", "end_turn",
                "content", List.of(Map.of("type", "text", "text", text))));
    }

    /** stop_reason=tool_use 응답 — 도구 호출 1건 */
    private JsonNode toolUseResponse(String toolName, String inputJson) throws Exception {
        return objectMapper.readTree("""
                {"stop_reason":"tool_use","content":[
                  {"type":"tool_use","id":"toolu_01","name":"%s","input":%s}
                ]}""".formatted(toolName, inputJson));
    }

    private static String decisionJson(String status, double confidence,
                                       String university, String name, String studentNo,
                                       String department, String birthDate) {
        return """
                {"status":"%s","confidence":%s,
                 "extracted":{"university":%s,"name":%s,"student_no":%s,"department":%s,"birth_date":%s},
                 "reason":"테스트 사유","user_message":"테스트 안내"}"""
                .formatted(status, confidence, q(university), q(name), q(studentNo), q(department), q(birthDate));
    }

    private static String q(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private static String validQr(String status, double confidence) {
        return decisionJson(status, confidence, "세종대학교", "홍길동", "24011234", "컴퓨터공학과", "2005-03-01");
    }

    private void respondWith(String text) throws Exception {
        when(claudeApiClient.createMessage(anyString(), anyList(), anyList())).thenReturn(finalResponse(text));
    }

    private AgentDecision runQr() {
        return agent.verify(IMAGE, "image/jpeg", VerificationMethod.SEJONG_QR);
    }

    private AgentDecision runEverytime() {
        return agent.verify(IMAGE, "image/jpeg", VerificationMethod.EVERYTIME_PROFILE);
    }

    @Nested
    @DisplayName("세종대 QR 경로 — 자동 승인 게이트")
    class SejongQrGate {

        @Test
        @DisplayName("다섯 항목 모두 유효 + 신뢰도 기준 이상이면 AUTO_APPROVED 유지")
        void autoApprovedWhenAllGatesPass() throws Exception {
            respondWith(validQr("AUTO_APPROVED", 0.95));

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.AUTO_APPROVED);
            assertThat(d.university()).isEqualTo("세종대학교");
            assertThat(d.name()).isEqualTo("홍길동");
            assertThat(d.studentNo()).isEqualTo("24011234");
            assertThat(d.department()).isEqualTo("컴퓨터공학과");
            assertThat(d.birthDate()).isEqualTo(LocalDate.of(2005, 3, 1));
            assertThat(d.llmCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("학과 누락이면 RETRY_REQUESTED로 강등 + 누락 항목을 안내문에 명시")
        void missingDepartmentDowngradesToRetry() throws Exception {
            respondWith(decisionJson("AUTO_APPROVED", 0.95, "세종대학교", "홍길동", "24011234", null, "2005-03-01"));

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.RETRY_REQUESTED);
            assertThat(d.reason()).startsWith("[게이트 강등: 필수 항목 누락");
            assertThat(d.userMessage()).contains("학과");
        }

        @Test
        @DisplayName("빈 문자열·\"null\" 문자열도 누락으로 취급")
        void blankAndNullStringTreatedAsMissing() throws Exception {
            respondWith(decisionJson("AUTO_APPROVED", 0.95, "세종대학교", "  ", "24011234", "null", "2005-03-01"));

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.RETRY_REQUESTED);
            assertThat(d.userMessage()).contains("이름").contains("학과");
        }

        @Test
        @DisplayName("타 대학이면 NEEDS_REVIEW로 강등 (자동 승인 불가)")
        void otherUniversityDowngradesToReview() throws Exception {
            respondWith(decisionJson("AUTO_APPROVED", 0.99, "서울대학교", "홍길동", "24011234", "컴퓨터공학과", "2005-03-01"));

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.NEEDS_REVIEW);
            assertThat(d.reason()).contains("대학명 불일치");
        }

        @Test
        @DisplayName("학번이 8자리 숫자가 아니면 NEEDS_REVIEW로 강등")
        void invalidStudentNoFormatDowngradesToReview() throws Exception {
            respondWith(decisionJson("AUTO_APPROVED", 0.99, "세종대학교", "홍길동", "2401123", "컴퓨터공학과", "2005-03-01"));

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.NEEDS_REVIEW);
            assertThat(d.reason()).contains("학번 형식 위반");
        }

        @Test
        @DisplayName("신뢰도가 임계값(0.85) 미만이면 NEEDS_REVIEW로 강등")
        void lowConfidenceDowngradesToReview() throws Exception {
            respondWith(validQr("AUTO_APPROVED", 0.80));

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.NEEDS_REVIEW);
            assertThat(d.reason()).contains("신뢰도 미달");
        }

        @Test
        @DisplayName("신뢰도가 정확히 임계값이면 통과 (경계값 포함)")
        void confidenceAtThresholdPasses() throws Exception {
            respondWith(validQr("AUTO_APPROVED", THRESHOLD));

            assertThat(runQr().status()).isEqualTo(VerificationStatus.AUTO_APPROVED);
        }

        @Test
        @DisplayName("생년월일 형식이 깨지면 누락으로 취급해 RETRY_REQUESTED")
        void malformedBirthDateTreatedAsMissing() throws Exception {
            respondWith(decisionJson("AUTO_APPROVED", 0.95, "세종대학교", "홍길동", "24011234", "컴퓨터공학과", "2005.03.01"));

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.RETRY_REQUESTED);
            assertThat(d.birthDate()).isNull();
            assertThat(d.userMessage()).contains("생년월일");
        }
    }

    @Nested
    @DisplayName("에브리타임 경로 — 학과·생년월일 무효화 + 입학년도 형식")
    class EverytimeGate {

        @Test
        @DisplayName("대학명·이름·입학년도만으로 AUTO_APPROVED, 학번·학과·생년월일은 null로 저장")
        void autoApprovedWithoutDepartmentAndBirthDate() throws Exception {
            respondWith(decisionJson("AUTO_APPROVED", 0.95, "세종대학교", "홍길동", "24학번", null, null));

            AgentDecision d = runEverytime();

            assertThat(d.status()).isEqualTo(VerificationStatus.AUTO_APPROVED);
            // "24학번"은 8자리 학번이 아니므로 유니크 인덱스 오염 방지를 위해 저장하지 않는다
            assertThat(d.studentNo()).isNull();
            assertThat(d.department()).isNull();
            assertThat(d.birthDate()).isNull();
        }

        @Test
        @DisplayName("LLM이 지시를 어기고 학과·생년월일을 채워도 서버가 무조건 null로 무효화")
        void serverNullifiesDepartmentAndBirthDateEvenIfLlmFillsThem() throws Exception {
            respondWith(decisionJson("AUTO_APPROVED", 0.95, "세종대학교", "홍길동", "24학번", "경영학과", "2005-03-01"));

            AgentDecision d = runEverytime();

            assertThat(d.status()).isEqualTo(VerificationStatus.AUTO_APPROVED);
            assertThat(d.department()).isNull();
            assertThat(d.birthDate()).isNull();
        }

        @Test
        @DisplayName("입학년도가 'XX학번' 형식이 아니면 NEEDS_REVIEW로 강등")
        void invalidEntryYearDowngradesToReview() throws Exception {
            respondWith(decisionJson("AUTO_APPROVED", 0.95, "세종대학교", "홍길동", "24011234", null, null));

            AgentDecision d = runEverytime();

            assertThat(d.status()).isEqualTo(VerificationStatus.NEEDS_REVIEW);
            assertThat(d.reason()).contains("입학년도 형식 위반");
        }

        @Test
        @DisplayName("에브리타임 경로는 도구 없이 호출된다 (학번 중복·학과 사전 조회 불가)")
        void noToolsOfferedOnEverytimePath() throws Exception {
            respondWith(decisionJson("AUTO_APPROVED", 0.95, "세종대학교", "홍길동", "24학번", null, null));

            runEverytime();

            verify(claudeApiClient).createMessage(anyString(), anyList(), eq(List.of()));
        }
    }

    @Nested
    @DisplayName("거절·상태 파싱 방어")
    class RejectionAndStatusParsing {

        @Test
        @DisplayName("REJECTED인데 신뢰도 0.9 이상이면 NEEDS_REVIEW로 강등 (과잉 거절 방어)")
        void highConfidenceRejectionBecomesReview() throws Exception {
            respondWith(decisionJson("REJECTED", 0.95, "세종대학교", "홍길동", "24011234", "컴퓨터공학과", "2005-03-01"));

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.NEEDS_REVIEW);
            assertThat(d.reason()).startsWith("[REJECTED→NEEDS_REVIEW");
        }

        @Test
        @DisplayName("REJECTED + 신뢰도 0.9 미만은 그대로 REJECTED")
        void lowConfidenceRejectionStays() throws Exception {
            respondWith(decisionJson("REJECTED", 0.6, "서울대학교", "홍길동", null, null, null));

            assertThat(runQr().status()).isEqualTo(VerificationStatus.REJECTED);
        }

        @Test
        @DisplayName("RETRY_REQUESTED는 게이트 없이 그대로 통과")
        void retryRequestedPassesThrough() throws Exception {
            respondWith(decisionJson("RETRY_REQUESTED", 0.3, null, null, null, null, null));

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.RETRY_REQUESTED);
            assertThat(d.userMessage()).isEqualTo("테스트 안내");
        }

        @Test
        @DisplayName("알 수 없는 status 값은 NEEDS_REVIEW")
        void unknownStatusBecomesReview() throws Exception {
            respondWith(decisionJson("MAYBE", 0.95, "세종대학교", "홍길동", "24011234", "컴퓨터공학과", "2005-03-01"));

            assertThat(runQr().status()).isEqualTo(VerificationStatus.NEEDS_REVIEW);
        }

        @Test
        @DisplayName("에이전트가 낼 수 없는 status(APPROVED, PENDING)는 NEEDS_REVIEW")
        void nonAgentStatusBecomesReview() throws Exception {
            respondWith(decisionJson("APPROVED", 0.95, "세종대학교", "홍길동", "24011234", "컴퓨터공학과", "2005-03-01"));
            assertThat(runQr().status()).isEqualTo(VerificationStatus.NEEDS_REVIEW);

            respondWith(decisionJson("PENDING", 0.95, "세종대학교", "홍길동", "24011234", "컴퓨터공학과", "2005-03-01"));
            assertThat(runQr().status()).isEqualTo(VerificationStatus.NEEDS_REVIEW);
        }
    }

    @Nested
    @DisplayName("응답 파서 방어")
    class ResponseParsing {

        @Test
        @DisplayName("코드 펜스와 잡담이 붙어도 JSON을 추출한다")
        void extractsJsonFromFencedChatter() throws Exception {
            respondWith("판정 결과입니다.\n```json\n" + validQr("AUTO_APPROVED", 0.95) + "\n```\n이상입니다.");

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.AUTO_APPROVED);
            assertThat(d.name()).isEqualTo("홍길동");
        }

        @Test
        @DisplayName("신뢰도는 0~1로 클램프되고, 누락 시 0.0")
        void confidenceClampedAndDefaulted() throws Exception {
            respondWith(validQr("AUTO_APPROVED", 1.7));
            assertThat(runQr().confidence()).isEqualTo(1.0);

            respondWith("""
                    {"status":"AUTO_APPROVED","extracted":{},"reason":"","user_message":""}""");
            AgentDecision d = runQr();
            assertThat(d.confidence()).isEqualTo(0.0);
            assertThat(d.status()).isNotEqualTo(VerificationStatus.AUTO_APPROVED);
        }

        @Test
        @DisplayName("text 블록이 없는 응답은 NEEDS_REVIEW 폴백 (예외 전파 없음)")
        void responseWithoutTextFallsBack() throws Exception {
            when(claudeApiClient.createMessage(anyString(), anyList(), anyList()))
                    .thenReturn(objectMapper.readTree("""
                            {"stop_reason":"end_turn","content":[]}"""));

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.NEEDS_REVIEW);
            assertThat(d.reason()).startsWith("에이전트 오류");
            assertThat(d.llmCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("JSON이 아닌 응답은 NEEDS_REVIEW 폴백")
        void nonJsonResponseFallsBack() throws Exception {
            respondWith("이미지를 분석할 수 없습니다.");

            assertThat(runQr().status()).isEqualTo(VerificationStatus.NEEDS_REVIEW);
        }
    }

    @Nested
    @DisplayName("멀티턴 루프 + fail-safe")
    class AgentLoop {

        @Test
        @DisplayName("tool_use 턴은 도구를 실행하고 결과를 이어 붙여 다음 턴을 진행한다")
        void toolUseTurnExecutesToolAndContinues() throws Exception {
            when(claudeApiClient.createMessage(anyString(), anyList(), anyList()))
                    .thenReturn(toolUseResponse("check_student_no", "{\"student_no\":\"24011234\"}"))
                    .thenReturn(finalResponse(validQr("AUTO_APPROVED", 0.95)));
            when(toolExecutor.execute(eq("check_student_no"), any()))
                    .thenReturn("{\"already_approved\":false}");

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.AUTO_APPROVED);
            assertThat(d.llmCalls()).isEqualTo(2);
            assertThat(d.actionsJson()).contains("check_student_no").contains("already_approved");
            verify(toolExecutor).execute(eq("check_student_no"), any());
        }

        @Test
        @DisplayName("도구 호출이 끝나지 않으면 최대 5턴에서 멈추고 NEEDS_REVIEW 폴백")
        void maxTurnsFallsBackToReview() throws Exception {
            when(claudeApiClient.createMessage(anyString(), anyList(), anyList()))
                    .thenReturn(toolUseResponse("search_department", "{\"department_name\":\"컴공\"}"));
            when(toolExecutor.execute(anyString(), any())).thenReturn("{\"existing_departments\":[]}");

            AgentDecision d = runQr();

            assertThat(d.status()).isEqualTo(VerificationStatus.NEEDS_REVIEW);
            assertThat(d.llmCalls()).isEqualTo(5);
            assertThat(d.reason()).contains("최대 턴 초과");
        }

        @Test
        @DisplayName("Claude API 호출이 실패해도 예외를 던지지 않고 NEEDS_REVIEW 폴백")
        void apiFailureFallsBackWithoutThrowing() {
            when(claudeApiClient.createMessage(anyString(), anyList(), anyList()))
                    .thenThrow(new IllegalStateException("Claude API 호출 실패"));

            assertThatCode(() -> {
                AgentDecision d = runQr();
                assertThat(d.status()).isEqualTo(VerificationStatus.NEEDS_REVIEW);
                assertThat(d.llmCalls()).isEqualTo(0);
                assertThat(d.processingMs()).isGreaterThanOrEqualTo(0);
            }).doesNotThrowAnyException();
            verify(toolExecutor, never()).execute(anyString(), any());
        }
    }
}
