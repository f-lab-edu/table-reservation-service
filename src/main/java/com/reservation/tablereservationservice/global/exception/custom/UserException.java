package com.reservation.tablereservationservice.global.exception.custom;

import com.reservation.tablereservationservice.global.exception.BusinessException;
import com.reservation.tablereservationservice.global.exception.ErrorCode;

public class UserException extends BusinessException {

	public UserException(ErrorCode errorCode) {
		super(errorCode);
	}
}
