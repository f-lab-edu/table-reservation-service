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
	INVALID_PARTY_SIZE("예약 인원 수가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
	RESERVATION_SLOT_NOT_OPENED("해당 날짜의 예약이 오픈되지 않았습니다.", HttpStatus.BAD_REQUEST),
	INVALID_RESERVATION_REQUEST("유효하지 않은 예약 요청입니다.", HttpStatus.BAD_REQUEST),
	RESERVATION_ALREADY_CANCELED("이미 취소된 예약입니다.", HttpStatus.BAD_REQUEST),
	RESERVATION_CANCEL_DEADLINE_PASSED("예약 취소는 방문 24시간 전까지 가능합니다.", HttpStatus.BAD_REQUEST),

	// 401 Unauthorized
	UNAUTHORIZED("인증되지 않은 사용자입니다.", HttpStatus.UNAUTHORIZED),
	INVALID_ACCESS_TOKEN("유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
	INVALID_PASSWORD("비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
	EXPIRED_TOKEN("만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),

	// 403 Forbidden
	ACCESS_DENIED("접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
	RESERVATION_FORBIDDEN("본인의 예약만 취소할 수 있습니다.", HttpStatus.FORBIDDEN),

	// 404 Not Found
	RESOURCE_NOT_FOUND("%s를(을) 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	// 409 Conflict
	DUPLICATE_RESOURCE("%s 값이 이미 존재합니다.", HttpStatus.CONFLICT),
	// 예약 정책 충돌
	RESERVATION_DUPLICATED_TIME("동시간대에 이미 예약이 존재합니다.", HttpStatus.CONFLICT),
	RESERVATION_CAPACITY_NOT_ENOUGH("예약 가능 좌석이 부족합니다.", HttpStatus.CONFLICT),
	// 동시성 충돌
	RESERVATION_NOT_AVAILABLE("다른 예약이 선점되었습니다.", HttpStatus.CONFLICT),

	// 500 Internal Server Error
	INTERNAL_SERVER_ERROR("서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

	private final String message;
	private final HttpStatus status;
	}
