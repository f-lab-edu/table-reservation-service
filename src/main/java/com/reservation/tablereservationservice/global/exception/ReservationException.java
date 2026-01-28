package com.reservation.tablereservationservice.global.exception;

public class ReservationException extends BusinessException {

	public ReservationException(ErrorCode errorCode, Object... args) {
		super(errorCode, args);
	}
}
