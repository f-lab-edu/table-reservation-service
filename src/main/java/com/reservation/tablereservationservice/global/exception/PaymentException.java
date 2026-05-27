package com.reservation.tablereservationservice.global.exception;

public class PaymentException extends BusinessException {

	public PaymentException(ErrorCode errorCode, Object... args) {
		super(errorCode, args);
	}
}
