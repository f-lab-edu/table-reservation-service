package com.reservation.tablereservationservice.presentation.user.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservation.tablereservationservice.application.user.service.UserService;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.GlobalExceptionHandler;
import com.reservation.tablereservationservice.global.exception.UserException;
import com.reservation.tablereservationservice.presentation.common.ApiResponse;
import com.reservation.tablereservationservice.presentation.user.dto.LoginRequestDto;
import com.reservation.tablereservationservice.presentation.user.dto.LoginResponseDto;
import com.reservation.tablereservationservice.presentation.user.dto.LoginUserResponseDto;
import com.reservation.tablereservationservice.presentation.user.dto.SignUpRequestDto;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private UserService userService;

	@Test
	@DisplayName("회원가입 요청 DTO 검증 실패 시 상세 에러 메시지 포함 400 응답 반환")
	void signUp_InvalidRequestDto_ReturnsBadRequest() throws Exception {
		// given
		SignUpRequestDto requestDto = new SignUpRequestDto(
			"not-email",
			"123!",
			"테스터테스터테스터테스터",
			"010-000-0000",
			"ADMIN"
		);

		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/users/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andReturn();

		// then
		ApiResponse<Map<String, String>> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<Map<String, String>>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(400);
		assertThat(response.getMessage()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());

		Map<String, String> errors = response.getData();
		assertThat(errors).isNotNull();
		assertThat(errors.get("email")).isEqualTo("이메일 형식이 올바르지 않습니다.");
		assertThat(errors.get("password")).isEqualTo("비밀번호는 8~20자의 영문 대소문자, 숫자, 특수문자를 포함해야 합니다.");
		assertThat(errors.get("name")).isEqualTo("이름은 8자 이하로 입력해야 합니다.");
		assertThat(errors.get("userRole")).isEqualTo("userRole은 CUSTOMER 또는 OWNER여야 합니다.");
	}

	@Test
	@DisplayName("회원가입 시 이메일 중복 발생 시 409 응답 반환")
	void signUp_DuplicateEmail_ReturnsConflict() throws Exception {
		// given
		SignUpRequestDto requestDto = new SignUpRequestDto(
			"dup@email.com",
			"Abcd1234!",
			"테스터",
			"010-1234-5678",
			"CUSTOMER"
		);

		given(userService.signUp(any()))
			.willThrow(new UserException(ErrorCode.DUPLICATE_RESOURCE, "email"));

		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/users/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andReturn();

		// then
		ApiResponse<Void> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<Void>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(409);
		assertThat(response.getMessage()).isEqualTo("email 값이 이미 존재합니다.");
		assertThat(response.getData()).isNull();
	}

	@Test
	@DisplayName("회원가입 시 휴대폰 번호 중복 발생 시 409 응답 반환")
	void signUp_DuplicatePhone_ReturnsConflict() throws Exception {
		// given
		SignUpRequestDto requestDto = new SignUpRequestDto(
			"test@email.com",
			"Abcd1234!",
			"테스터",
			"010-1234-5678",
			"CUSTOMER"
		);

		given(userService.signUp(any()))
			.willThrow(new UserException(ErrorCode.DUPLICATE_RESOURCE, "phone"));

		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/users/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andReturn();

		// then
		ApiResponse<Void> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<Void>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(409);
		assertThat(response.getMessage()).isEqualTo("phone 값이 이미 존재합니다.");
		assertThat(response.getData()).isNull();
	}

	@Test
	@DisplayName("로그인 성공 시 200 + accessToken 응답 반환")
	void login_Success_ReturnsOkWithToken() throws Exception {
		// given
		LoginRequestDto requestDto = new LoginRequestDto("test@email.com", "Abcd1234!");

		LoginResponseDto serviceResult = LoginResponseDto.builder()
			.email("test@email.com")
			.userRole("CUSTOMER")
			.accessToken("access.token.value")
			.build();

		given(userService.login("test@email.com", "Abcd1234!"))
			.willReturn(serviceResult);

		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/users/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andReturn();

		// then
		ApiResponse<LoginResponseDto> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<LoginResponseDto>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getMessage()).isEqualTo("로그인 성공");

		LoginResponseDto data = response.getData();
		assertThat(data).isNotNull();
		assertThat(data.getEmail()).isEqualTo("test@email.com");
		assertThat(data.getUserRole()).isEqualTo("CUSTOMER");
		assertThat(data.getAccessToken()).isEqualTo("access.token.value");
	}

	@Test
	@DisplayName("로그인 요청 DTO 검증 실패 시 400")
	void login_InvalidRequestDto_ReturnsBadRequest() throws Exception {
		// given
		LoginRequestDto requestDto = new LoginRequestDto("not-email", "");

		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/users/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andReturn();

		// then
		ApiResponse<Map<String, String>> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<Map<String, String>>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(400);
		assertThat(response.getMessage()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());
		assertThat(response.getData()).isNotNull();
	}

	@Test
	@DisplayName("로그인 실패 - 비밀번호 불일치면 401")
	void login_InvalidPassword_ReturnsUnauthorized() throws Exception {
		// given
		LoginRequestDto requestDto = new LoginRequestDto("test@email.com", "wrongPassword!");

		doThrow(new UserException(ErrorCode.INVALID_PASSWORD))
			.when(userService).login("test@email.com", "wrongPassword!");

		// when
		MvcResult mvcResult = mockMvc.perform(post("/api/users/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(requestDto)))
			.andReturn();

		// then
		ApiResponse<Void> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<Void>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(401);
		assertThat(response.getMessage()).isEqualTo(ErrorCode.INVALID_PASSWORD.getMessage());
		assertThat(response.getData()).isNull();
	}

	@Test
	@DisplayName("내 정보 조회 성공 - Authentication principal(email)로 /me 호출")
	void me_Success() throws Exception {
		// given
		String email = "test@email.com";

		LoginUserResponseDto serviceResult = LoginUserResponseDto.builder()
			.userId(1L)
			.email(email)
			.name("테스터")
			.userRole("CUSTOMER")
			.build();

		given(userService.getCurrentUser(email)).willReturn(serviceResult);

		// when
		MvcResult mvcResult = mockMvc.perform(get("/api/users/me")
				.principal(new UsernamePasswordAuthenticationToken(email, null)))
			.andReturn();

		// then
		ApiResponse<LoginUserResponseDto> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<LoginUserResponseDto>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(200);
		assertThat(response.getMessage()).isEqualTo("사용자 정보 조회 성공");

		LoginUserResponseDto data = response.getData();
		assertThat(data).isNotNull();
		assertThat(data.getUserId()).isEqualTo(1L);
		assertThat(data.getEmail()).isEqualTo(email);
		assertThat(data.getName()).isEqualTo("테스터");
		assertThat(data.getUserRole()).isEqualTo("CUSTOMER");
	}

	@Test
	@DisplayName("내 정보 조회 실패 - 존재하지 않는 이메일이면 404")
	void me_Fail_UserNotFound() throws Exception {
		// given
		String email = "missing@email.com";

		given(userService.getCurrentUser(email))
			.willThrow(new UserException(ErrorCode.RESOURCE_NOT_FOUND, "User"));

		// when
		MvcResult mvcResult = mockMvc.perform(get("/api/users/me")
				.principal(new UsernamePasswordAuthenticationToken(email, null)))
			.andReturn();

		// then
		ApiResponse<Void> response = readResponse(
			mvcResult,
			new TypeReference<ApiResponse<Void>>() {
			}
		);

		assertThat(response.getCode()).isEqualTo(404);
		assertThat(response.getMessage()).isEqualTo("User를(을) 찾을 수 없습니다.");
		assertThat(response.getData()).isNull();
	}

	private <T> ApiResponse<T> readResponse(
		MvcResult mvcResult,
		TypeReference<ApiResponse<T>> typeRef
	) throws Exception {
		String body = mvcResult.getResponse().getContentAsString();
		return objectMapper.readValue(body, typeRef);
	}
}
