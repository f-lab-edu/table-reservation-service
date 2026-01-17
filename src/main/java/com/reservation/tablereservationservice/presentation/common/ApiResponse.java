package com.reservation.tablereservationservice.presentation.common;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

	private int code;
	private String message;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private T data;

	public static <T> ApiResponse<T> success(String message) {
		return success(HttpStatus.OK, message, null);
	}

	public static <T> ApiResponse<T> success(String message, T data) {
		return success(HttpStatus.OK, message, data);
	}

	public static <T> ApiResponse<T> success(HttpStatus status, String message) {
		return success(status, message, null);
	}

	public static <T> ApiResponse<T> success(HttpStatus status, String message, T data) {
		return new ApiResponse<>(status.value(), message, data);
	}

	public static <T> ApiResponse<T> error(HttpStatus status, String message) {
		return new ApiResponse<>(status.value(), message, null);
	}

	public static <T> ApiResponse<T> error(HttpStatus status, String message, T data) {
		return new ApiResponse<>(status.value(), message, data);
	}
}
