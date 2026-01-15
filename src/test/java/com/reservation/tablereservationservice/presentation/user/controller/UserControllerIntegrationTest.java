package com.reservation.tablereservationservice.presentation.user.controller;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.reservation.tablereservationservice.presentation.user.dto.LoginRequestDto;
import com.reservation.tablereservationservice.presentation.user.dto.SignUpRequestDto;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class UserControllerIntegrationTest {

	@LocalServerPort
	private int port;

	@BeforeEach
	void setUp() {
		RestAssured.port = port;
	}

	@Test
	@DisplayName("회원가입 성공")
	void signUp_Success() {
		SignUpRequestDto request = new SignUpRequestDto(
			"new@email.com",
			"Abcd1234!",
			"홍길동",
			"010-2222-3333",
			"CUSTOMER"
		);

		given()
			.contentType(ContentType.JSON)
			.body(request)
		.when()
			.post("/api/users/signup")
		.then()
			.statusCode(200)
			.body("code", equalTo(200));
	}

	@Test
	@DisplayName("회원가입 후 로그인 성공")
	void login_Success_AfterSignUp() {
		SignUpRequestDto signUpRequest = new SignUpRequestDto(
			"login@email.com",
			"Abcd1234!",
			"홍길동",
			"010-9999-8888",
			"CUSTOMER"
		);

		LoginRequestDto loginRequest = new LoginRequestDto(
			"login@email.com",
			"Abcd1234!"
		);

		// 회원가입
		given()
			.contentType(ContentType.JSON)
			.body(signUpRequest)
		.when()
			.post("/api/users/signup")
		.then()
			.statusCode(200)
			.body("code", equalTo(200));

		// 로그인
		given()
			.contentType(ContentType.JSON)
			.body(loginRequest)
		.when()
			.post("/api/users/login")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("로그인 성공"))
			.body("data.accessToken", notNullValue());
	}

	@Test
	@DisplayName("로그인 실패 - 존재하지 않는 이메일이면 404 반환")
	void login_Fail_UserNotFound() {
		LoginRequestDto request = new LoginRequestDto(
			"notfound@email.com",
			"Abcd1234!"
		);

		given()
			.contentType(ContentType.JSON)
			.body(request)
		.when()
			.post("/api/users/login")
		.then()
			.statusCode(404)
			.body("code", equalTo(404));
	}

	@Test
	@DisplayName("로그인 실패 - 비밀번호 불일치면 401 반환")
	void login_Fail_InvalidPassword() {
		SignUpRequestDto signUpRequest = new SignUpRequestDto(
			"wrongpw@email.com",
			"Abcd1234!",
			"홍길동",
			"010-7777-6666",
			"CUSTOMER"
		);

		LoginRequestDto wrongLoginRequest = new LoginRequestDto(
			"wrongpw@email.com",
			"Wrong1234!"
		);

		given()
			.contentType(ContentType.JSON)
			.body(signUpRequest)
		.when()
			.post("/api/users/signup")
		.then()
			.statusCode(200)
			.body("code", equalTo(200));


		given()
			.contentType(ContentType.JSON)
			.body(wrongLoginRequest)
		.when()
			.post("/api/users/login")
		.then()
			.statusCode(401)
			.body("code", equalTo(401));
	}
}
