package com.reservation.tablereservationservice.global.exception;

public class UserException extends BusinessException {

	public UserException(ErrorCode errorCode, Object... args) {
		super(errorCode, args);
	}
}
