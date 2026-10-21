package com.campushanjang.domain.verification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Claude API 클라이언트의 재시도 정책 테스트.
 *
 * <p>실제 네트워크 대신 ExchangeFunction을 바꿔치기해 상태코드 시퀀스를 재현한다.
 * 정책: 429·5xx만 지수 백오프 재시도, 그 외 4xx는 즉시 실패.
 */
class ClaudeApiClientRetryTest {

    private static final int MAX_RETRIES = 3;
    private static final String OK_BODY = """
            {"stop_reason":"end_turn","content":[{"type":"text","text":"{}"}]}""";

    /** 호출 순서대로 상태코드를 돌려주는 가짜 서버. 시퀀스를 넘기면 마지막 값을 반복한다. */
    private static final class ScriptedServer implements ExchangeFunction {
        final AtomicInteger calls = new AtomicInteger();
        private final int[] statuses;

        ScriptedServer(int... statuses) {
            this.statuses = statuses;
        }

        @Override
        public Mono<ClientResponse> exchange(org.springframework.web.reactive.function.client.ClientRequest request) {
            int idx = Math.min(calls.getAndIncrement(), statuses.length - 1);
            int status = statuses[idx];
            String body = status == 200 ? OK_BODY : "{\"type\":\"error\",\"error\":{\"type\":\"x\"}}";
            return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(status))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        }
    }

    private static ClaudeApiClient clientWith(ScriptedServer server) {
        // 백오프 1ms — 테스트 속도용. 실제 값은 application.yml의 claude.retry.*
        return new ClaudeApiClient(WebClient.builder().exchangeFunction(server),
                "test-key", "test-model", 256, MAX_RETRIES, 1);
    }

    private static JsonNode call(ClaudeApiClient client) {
        return client.createMessage("system", List.of(ClaudeApiClient.userMessage(
                List.of(ClaudeApiClient.textBlock("hi")))), List.of());
    }

    @Test
    @DisplayName("529 overloaded 두 번 뒤 200이면 재시도 끝에 정상 응답을 돌려준다")
    void retriesOnOverloadedThenSucceeds() {
        ScriptedServer server = new ScriptedServer(529, 529, 200);

        JsonNode res = call(clientWith(server));

        assertThat(res.path("stop_reason").asText()).isEqualTo("end_turn");
        assertThat(server.calls.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("429 rate limit도 재시도 대상이다")
    void retriesOnRateLimit() {
        ScriptedServer server = new ScriptedServer(429, 200);

        call(clientWith(server));

        assertThat(server.calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("400 요청 오류는 재시도하지 않고 즉시 실패한다")
    void doesNotRetryOnBadRequest() {
        ScriptedServer server = new ScriptedServer(400, 200);

        assertThatThrownBy(() -> call(clientWith(server)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Claude API 호출 실패");
        assertThat(server.calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("재시도 횟수를 다 쓰면 실패하고, 총 시도는 1 + maxRetries 회다")
    void failsAfterRetriesExhausted() {
        ScriptedServer server = new ScriptedServer(503);

        assertThatThrownBy(() -> call(clientWith(server)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(server.calls.get()).isEqualTo(1 + MAX_RETRIES);
    }

    @Test
    @DisplayName("401 인증 오류는 재시도 대상이 아니다")
    void doesNotRetryOnUnauthorized() {
        ScriptedServer server = new ScriptedServer(401, 200);

        assertThatThrownBy(() -> call(clientWith(server))).isInstanceOf(IllegalStateException.class);
        assertThat(server.calls.get()).isEqualTo(1);
    }
}
