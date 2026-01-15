package com.reservation.tablereservationservice.global.jwt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.JwtAuthenticationException;
import com.reservation.tablereservationservice.presentation.common.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException authException) throws IOException {

		ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

		if (authException instanceof JwtAuthenticationException jwtEx) {
			errorCode = jwtEx.getErrorCode();
		}

		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());

		ApiResponse<Void> body = ApiResponse.error(errorCode.getStatus(), errorCode.getMessage());
		response.getWriter().write(objectMapper.writeValueAsString(body));
	}
}
