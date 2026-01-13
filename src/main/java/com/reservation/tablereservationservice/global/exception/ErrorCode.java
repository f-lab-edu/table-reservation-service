package com.reservation.tablereservationservice.global.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

	// 400 Bad Request
	INVALID_INPUT_VALUE("잘못된 입력 값입니다.", HttpStatus.BAD_REQUEST),
	MISSING_PARAMETER("필수 파라미터가 누락되었습니다.", HttpStatus.BAD_REQUEST),

	// 401 Unauthorized
	UNAUTHORIZED("인증되지 않은 사용자입니다.", HttpStatus.UNAUTHORIZED),
	INVALID_ACCESS_TOKEN("유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
	INVALID_PASSWORD("비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
	EXPIRED_TOKEN("만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),

	// 403 Forbidden
	ACCESS_DENIED("접근 권한이 없습니다.", HttpStatus.FORBIDDEN),

	// 404 Not Found
	USER_NOT_FOUND("존재하지 않는 사용자입니다.", HttpStatus.NOT_FOUND),

	// 409 Conflict
	DUPLICATE_EMAIL("이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
	DUPLICATE_PHONE("이미 등록된 전화번호입니다.", HttpStatus.CONFLICT),

	// 500 Internal Server Error
	INTERNAL_SERVER_ERROR("서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

	private final String message;
	private final HttpStatus status;
}
