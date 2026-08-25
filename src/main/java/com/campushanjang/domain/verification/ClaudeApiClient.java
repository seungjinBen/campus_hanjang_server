package com.campushanjang.domain.verification;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Claude Messages API 클라이언트 — KakaoOAuthClient와 동일하게 WebClient 직접 호출 (의존성 최소화)
@Slf4j
@Component
public class ClaudeApiClient {

    private final WebClient webClient;
    private final String model;
    private final int maxTokens;

    public ClaudeApiClient(
            @Value("${claude.api-key}") String apiKey,
            @Value("${claude.model:claude-haiku-4-5-20251001}") String model,
            @Value("${claude.max-tokens:2048}") int maxTokens
    ) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                // base64 이미지 포함 요청/응답 대응 — 기본 256KB 제한으로는 부족
                .codecs(c -> c.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                .build();
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public JsonNode createMessage(String system, List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", system);
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        try {
            return webClient.post()
                    .uri("/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(60));
        } catch (WebClientResponseException e) {
            // 응답 body에 사용자 이미지 정보가 없으므로 상태코드와 에러 본문만 로그
            log.error("Claude API 오류 status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Claude API 호출 실패", e);
        } catch (Exception e) {
            log.error("Claude API 호출 실패 model={} error={}", model, e.getMessage());
            throw new IllegalStateException("Claude API 호출 실패", e);
        }
    }

    // ── 메시지/콘텐츠 블록 빌더 헬퍼 ──

    public static Map<String, Object> userMessage(List<Map<String, Object>> content) {
        return Map.of("role", "user", "content", content);
    }

    // tool_use 턴을 이어가려면 assistant 응답의 content를 그대로 대화에 보존해야 함
    public static Map<String, Object> assistantMessageFrom(JsonNode response) {
        List<Object> content = new ArrayList<>();
        response.path("content").forEach(content::add);
        return Map.of("role", "assistant", "content", content);
    }

    public static Map<String, Object> textBlock(String text) {
        return Map.of("type", "text", "text", text);
    }

    public static Map<String, Object> imageBlock(String mediaType, String base64Data) {
        return Map.of(
                "type", "image",
                "source", Map.of(
                        "type", "base64",
                        "media_type", mediaType,
                        "data", base64Data
                )
        );
    }

    public static Map<String, Object> toolResultBlock(String toolUseId, String resultJson) {
        return Map.of(
                "type", "tool_result",
                "tool_use_id", toolUseId,
                "content", resultJson
        );
    }
}
