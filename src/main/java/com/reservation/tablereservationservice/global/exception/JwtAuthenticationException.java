package com.reservation.tablereservationservice.global.exception;

import org.springframework.security.core.AuthenticationException;

import lombok.Getter;

@Getter
public class JwtAuthenticationException extends AuthenticationException {

	private final ErrorCode errorCode;

	public JwtAuthenticationException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}
}
