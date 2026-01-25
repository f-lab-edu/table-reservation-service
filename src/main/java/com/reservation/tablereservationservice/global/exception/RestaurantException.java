package com.reservation.tablereservationservice.global.exception;

public class RestaurantException extends BusinessException {

	public RestaurantException(ErrorCode errorCode, Object... args) {
		super(errorCode, args);
	}
}
