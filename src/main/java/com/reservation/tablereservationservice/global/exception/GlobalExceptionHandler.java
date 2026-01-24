package com.reservation.tablereservationservice.global.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.reservation.tablereservationservice.presentation.common.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();

		String message = buildMessage(errorCode, e.getArgs());

		return ResponseEntity
			.status(errorCode.getStatus())
			.body(ApiResponse.error(errorCode.getStatus(), message));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(
		MethodArgumentNotValidException e) {
		Map<String, String> errors = new LinkedHashMap<>();

		for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
			// 같은 필드 에러가 여러 개면 첫 번째 메시지만 유지
			errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}

		ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

		return ResponseEntity
			.status(errorCode.getStatus())
			.body(ApiResponse.error(errorCode.getStatus(), errorCode.getMessage(), errors));
	}

	@ExceptionHandler(AuthorizationDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthorizationDeniedException(AuthorizationDeniedException e) {
		log.warn("Authorization denied: {}", e.getMessage());

		ErrorCode errorCode = ErrorCode.ACCESS_DENIED; // 403

		return ResponseEntity
			.status(errorCode.getStatus())
			.body(ApiResponse.error(errorCode.getStatus(), errorCode.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		log.error("Unhandled exception", e);

		ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

		return ResponseEntity
			.status(errorCode.getStatus())
			.body(ApiResponse.error(errorCode.getStatus(), errorCode.getMessage()));
	}

	private String buildMessage(ErrorCode errorCode, Object[] args) {
		String template = errorCode.getMessage();

		// args가 없으면 템플릿 그대로 반환
		if (args == null || args.length == 0) {
			return template;
		}

		try {
			return String.format(template, args);
		} catch (Exception formatException) {
			log.warn("Failed to format error message. code={}, template={}, args={}",
				errorCode.name(), template, args, formatException);
			return template;
		}
	}
}
