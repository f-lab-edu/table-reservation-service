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
import com.reservation.tablereservationservice.presentation.user.dto.LoginResponseDto;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	@DisplayName("회원가입 요청 DTO 검증 실패 시 상세 에러 메시지 포함 400 응답 반환")
	void signUp_InvalidRequestDto_ReturnsBadRequest() throws Exception {
		// given
		String invalidJson = """
			{
			  "email": "not-email",
			  "password": "123",
			  "name": "홍길동홍길동홍길동홍길동",
			  "phone": "010-000-0000",
			  "userRole": "ADMIN"
			}
			""";

		// when & then
		mockMvc.perform(post("/api/users/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidJson))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(400))
			.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT_VALUE.getMessage()))

			// 구체적인 필드 에러 메시지 검증 (SignUpRequestDto에 정의한 메시지들)
			.andExpect(jsonPath("$.data.email").value("이메일 형식이 올바르지 않습니다."))
			.andExpect(jsonPath("$.data.password").value("비밀번호는 8~20자의 영문 대소문자, 숫자, 특수문자를 포함해야 합니다."))
			.andExpect(jsonPath("$.data.name").value("이름은 8자 이하로 입력해야 합니다."))
			.andExpect(jsonPath("$.data.userRole").value("userRole은 CUSTOMER 또는 OWNER여야 합니다."));

		// DTO 검증에서 막혔으니 서비스 호출 안되는지 검증
		then(userService).shouldHaveNoInteractions();
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
			.willThrow(new UserException(ErrorCode.DUPLICATE_RESOURCE, "email"));

		mockMvc.perform(post("/api/users/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validJson))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(409))
			.andExpect(jsonPath("$.message").value("email 값이 이미 존재합니다."));
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
			.willThrow(new UserException(ErrorCode.DUPLICATE_RESOURCE, "phone"));

		mockMvc.perform(post("/api/users/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validJson))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(409))
			.andExpect(jsonPath("$.message").value("phone 값이 이미 존재합니다."));
	}

	@Test
	@DisplayName("로그인 성공 시 200 + accessToken 응답 반환")
	void login_Success_ReturnsOkWithToken() throws Exception {
		// given
		String validJson = """
			{
			  "email": "test@email.com",
			  "password": "Abcd1234!"
			}
			""";

		LoginResponseDto result = LoginResponseDto.builder()
			.accessToken("access.token.value")
			.build();

		given(userService.login(eq("test@email.com"), eq("Abcd1234!")))
			.willReturn(result);

		// when & then
		mockMvc.perform(post("/api/users/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validJson))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(200))
			.andExpect(jsonPath("$.message").value("로그인 성공"))
			.andExpect(jsonPath("$.data.accessToken").value("access.token.value"));

	}

	@Test
	@DisplayName("로그인 요청 DTO 검증 실패 시 400 응답 반환")
	void login_InvalidRequestDto_ReturnsBadRequest() throws Exception {
		// given: email 형식 오류 + password 공백
		String invalidJson = """
			{
			  "email": "not-email",
			  "password": ""
			}
			""";

		// when & then
		mockMvc.perform(post("/api/users/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidJson))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(400))
			.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_INPUT_VALUE.getMessage()));

		// DTO 검증에서 막혔으니 서비스 호출 안되는지 검증
		then(userService).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("로그인 실패 - 비밀번호 불일치면 401 응답 반환")
	void login_InvalidPassword_ReturnsUnauthorized() throws Exception {
		// given
		String validJson = """
			{
			  "email": "test@email.com",
			  "password": "wrongPassword!"
			}
			""";

		given(userService.login(eq("test@email.com"), eq("wrongPassword!")))
			.willThrow(new UserException(ErrorCode.INVALID_PASSWORD));

		// when & then
		mockMvc.perform(post("/api/users/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validJson))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(401))
			.andExpect(jsonPath("$.message").value(ErrorCode.INVALID_PASSWORD.getMessage()));
	}

}
