package com.campushanjang.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 인증
    UNAUTHORIZED("UNAUTHORIZED", "인증이 필요해요", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "토큰이 만료됐어요", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "접근 권한이 없어요", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 틀렸어요", HttpStatus.UNAUTHORIZED),
    WITHDRAWAL_REREGISTRATION_BLOCKED("WITHDRAWAL_REREGISTRATION_BLOCKED", "탈퇴 후 24시간 이내에는 재가입이 불가능해요", HttpStatus.FORBIDDEN),

    // 프로필
    NICKNAME_TAKEN("NICKNAME_TAKEN", "이미 사용 중인 닉네임이에요", HttpStatus.CONFLICT),
    PROFILE_INCOMPLETE("PROFILE_INCOMPLETE", "프로필을 먼저 완성해 주세요", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "존재하지 않거나 접근할 수 없어요", HttpStatus.NOT_FOUND),

    // 매칭
    MATCHING_NOT_AVAILABLE("MATCHING_NOT_AVAILABLE", "매칭 기능은 아직 오픈되지 않았어요", HttpStatus.FORBIDDEN),
    MATCHING_TERMINATED("MATCHING_TERMINATED", "캠퍼스한장 매칭 서비스가 종료됐어요", HttpStatus.GONE),
    DAILY_SELECT_LIMIT_EXCEEDED("DAILY_SELECT_LIMIT_EXCEEDED", "오늘 선택 횟수를 모두 사용했어요. 내일 자정에 초기화돼요", HttpStatus.CONFLICT),
    NO_CANDIDATES("NO_CANDIDATES", "아직 매칭 가능한 유저가 없어요", HttpStatus.NOT_FOUND),
    CANDIDATE_NOT_FOUND("CANDIDATE_NOT_FOUND", "유효하지 않은 후보예요", HttpStatus.BAD_REQUEST),
    NOTE_TOO_LONG("NOTE_TOO_LONG", "쪽지는 50자 이내로 작성해 주세요", HttpStatus.BAD_REQUEST),
    NOTE_NOT_FOUND("NOTE_NOT_FOUND", "쪽지를 찾을 수 없어요", HttpStatus.NOT_FOUND),
    NOTE_ALREADY_RESPONDED("NOTE_ALREADY_RESPONDED", "이미 응답한 쪽지예요", HttpStatus.CONFLICT),
    NOTE_ALREADY_SENT("NOTE_ALREADY_SENT", "이미 쪽지를 보낸 상대예요", HttpStatus.CONFLICT),

    // 학생인증
    STUDENT_VERIFICATION_REQUIRED("STUDENT_VERIFICATION_REQUIRED", "학생인증을 완료해야 이용할 수 있어요", HttpStatus.FORBIDDEN),
    VERIFICATION_ALREADY_APPROVED("VERIFICATION_ALREADY_APPROVED", "이미 학생인증이 완료됐어요", HttpStatus.CONFLICT),
    VERIFICATION_DUPLICATE_IMAGE("VERIFICATION_DUPLICATE_IMAGE", "이미 사용된 캡처 이미지예요. 본인의 학생앱 화면을 새로 캡처해 주세요", HttpStatus.CONFLICT),
    VERIFICATION_STUDENT_NO_TAKEN("VERIFICATION_STUDENT_NO_TAKEN", "이미 인증에 사용된 학번이에요", HttpStatus.CONFLICT),
    VERIFICATION_NOT_FOUND("VERIFICATION_NOT_FOUND", "인증 요청 내역이 없어요", HttpStatus.NOT_FOUND),
    VERIFICATION_INVALID_STATE("VERIFICATION_INVALID_STATE", "처리할 수 없는 인증 상태예요", HttpStatus.CONFLICT),

    // 사진
    PHOTO_TOO_LARGE("PHOTO_TOO_LARGE", "사진은 10MB 이하만 업로드할 수 있어요", HttpStatus.BAD_REQUEST),
    PHOTO_INVALID_FORMAT("PHOTO_INVALID_FORMAT", "JPEG, PNG, WEBP, HEIC, HEIF 형식만 가능해요", HttpStatus.BAD_REQUEST),
    PHOTO_UPLOAD_FAILED("PHOTO_UPLOAD_FAILED", "사진을 불러오지 못했어요. 다른 사진으로 시도해 주세요 🥲", HttpStatus.INTERNAL_SERVER_ERROR),

    // 서버
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "잠시 후 다시 시도해 주세요", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR("INTERNAL_ERROR", "서버 오류가 발생했어요", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
