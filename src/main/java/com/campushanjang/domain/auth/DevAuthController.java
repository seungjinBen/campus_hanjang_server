package com.campushanjang.domain.auth;

import com.campushanjang.common.response.ApiResponse;
import com.campushanjang.domain.user.UserRepository;
import com.campushanjang.domain.user.entity.User;
import com.campushanjang.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * k6 부하 테스트 전용 JWT 발급 컨트롤러.
 * dev 프로파일에서만 등록 — prod 빌드에서는 이 Bean이 존재하지 않음.
 * kakao_id 'test_' 접두사 유저에게만 토큰 발급 → 실사용자 토큰 남발 방지.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevAuthController {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    /**
     * 테스트 유저 JWT 일괄 발급.
     * k6 setup() 에서 한 번 호출해 전체 토큰 풀을 로드한다.
     *
     * @param gender "MALE" | "FEMALE"
     * @param limit  최대 반환 수 (기본 500)
     */
    @GetMapping("/test-tokens")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getTestTokens(
            @RequestParam(defaultValue = "MALE") String gender,
            @RequestParam(defaultValue = "500") int limit
    ) {
        String prefix = "test_" + gender.toLowerCase() + "_";
        List<User> users = userRepository.findByKakaoIdStartingWithAndIsActiveTrue(
                prefix, PageRequest.of(0, limit));

        List<Map<String, String>> tokens = users.stream()
                .map(u -> Map.of(
                        "userId", u.getId().toString(),
                        "token", jwtProvider.issueAccessToken(
                                u.getId().toString(),
                                u.getGender().name(),
                                u.getRole().name()
                        )
                ))
                .collect(Collectors.toList());

        log.info("k6 테스트 토큰 발급 gender={} count={}", gender, tokens.size());
        return ResponseEntity.ok(ApiResponse.ok(tokens));
    }
}
