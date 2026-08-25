package com.campushanjang.domain.verification.agent;

// 에이전트가 수행한 도구 호출 1건 — agent_actions JSONB에 배열로 직렬화되어 감사 추적에 사용
public record AgentAction(
        String tool,
        String input,
        String result
) {
}
