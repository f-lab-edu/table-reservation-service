package com.reservation.tablereservationservice.presentation.user.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.reservation.tablereservationservice.application.user.service.UserService;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.GlobalExceptionHandler;
import com.reservation.tablereservationservice.global.exception.UserException;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	@DisplayName("회원가입 요청 DTO 검증 실패 시 400 응답 반환")
	void signUp_InvalidRequestDto_ReturnsBadRequest() throws Exception {
		String invalidJson = """
			{
			  "email": "not-email",
			  "password": "123",
			  "name": "홍길동홍길동홍길동홍길동",
			  "phone": "010-000-0000",
			  "userRole": "ADMIN"
			}
			""";

		mockMvc.perform(post("/api/users/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidJson))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(400))
			.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT_VALUE.getMessage()));
	}

	@Test
	@DisplayName("회원가입 시 이메일 중복 발생 시 409 응답 반환")
	void signUp_DuplicateEmail_ReturnsConflict() throws Exception {
		String validJson = """
			{
			  "email": "dup@email.com",
			  "password": "Abcd1234!",
			  "name": "홍길동",
			  "phone": "010-1234-5678",
			  "userRole": "CUSTOMER"
			}
			""";

		given(userService.signUp(any()))
			.willThrow(new UserException(ErrorCode.DUPLICATE_EMAIL));

		mockMvc.perform(post("/api/users/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validJson))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(409))
			.andExpect(jsonPath("$.message").value(ErrorCode.DUPLICATE_EMAIL.getMessage()));
	}

	@Test
	@DisplayName("회원가입 시 휴대폰 번호 중복 발생 시 409 응답 반환")
	void signUp_DuplicatePhone_ReturnsConflict() throws Exception {
		String validJson = """
			{
			  "email": "test@email.com",
			  "password": "Abcd1234!",
			  "name": "홍길동",
			  "phone": "010-1234-5678",
			  "userRole": "CUSTOMER"
			}
			""";

		given(userService.signUp(any()))
			.willThrow(new UserException(ErrorCode.DUPLICATE_PHONE));

		mockMvc.perform(post("/api/users/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validJson))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(409))
			.andExpect(jsonPath("$.message").value(ErrorCode.DUPLICATE_PHONE.getMessage()));
	}
}