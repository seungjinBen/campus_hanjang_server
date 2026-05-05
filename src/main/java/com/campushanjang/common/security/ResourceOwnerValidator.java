package com.campushanjang.common.security;

import com.campushanjang.common.exception.BusinessException;
import com.campushanjang.common.exception.ErrorCode;
import com.campushanjang.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class ResourceOwnerValidator {

    /**
     * 요청한 유저가 리소스 소유자인지 검증한다.
     * JWT의 userId와 전달받은 resourceOwnerId가 다르면 즉시 차단한다.
     * 403 대신 404로 응답해 리소스 존재 여부 자체를 노출하지 않는다.
     */
    public void validateOwner(UUID resourceOwnerId) {
        UUID requesterId = SecurityUtil.getCurrentUserId();
        if (!requesterId.equals(resourceOwnerId)) {
            log.warn("타인 리소스 접근 시도 차단 requesterId={} resourceOwnerId={}",
                    requesterId, resourceOwnerId);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
