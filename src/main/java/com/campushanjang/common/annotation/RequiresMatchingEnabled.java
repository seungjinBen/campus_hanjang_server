package com.campushanjang.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresMatchingEnabled {
    // true로 설정하면 서비스 종료 후에도 해당 엔드포인트를 차단하지 않는다 (수신함 조회용)
    boolean allowAfterTermination() default false;
}
