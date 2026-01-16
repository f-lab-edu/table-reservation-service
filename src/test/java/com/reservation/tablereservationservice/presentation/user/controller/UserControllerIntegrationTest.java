package com.reservation.tablereservationservice.presentation.user.controller;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.presentation.user.dto.LoginRequestDto;
import com.reservation.tablereservationservice.presentation.user.dto.SignUpRequestDto;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserControllerIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		RestAssured.port = port;
		userRepository.deleteAll();
	}

	@Test
	@DisplayName("회원가입 성공")
	void signUp_Success() {
		SignUpRequestDto request = createTestSignUpRequest("test_signup@email.com", "010-2222-3333", "테스터");

		given()
			.contentType(ContentType.JSON)
			.body(request)
		.when()
			.post("/api/users/signup")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("회원 가입 성공"));
	}

	@Test
	@DisplayName("회원가입 후 로그인 성공")
	void login_Success_AfterSignUp() {
		String email = "test_login@email.com";

		SignUpRequestDto signUpRequest = createTestSignUpRequest(email, "010-9999-8888", "테스터");
		LoginRequestDto loginRequest = createTestLoginRequest(email, "Abcd1234!");

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
			.body(loginRequest)
		.when()
			.post("/api/users/login")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("로그인 성공"))
			.body("data.accessToken", notNullValue())
			.body("data.email", equalTo(email));
	}

	@Test
	@DisplayName("회원가입 실패 - 이메일 중복이면 409 반환")
	void signUp_Fail_DuplicateEmail() {
		SignUpRequestDto testRequest = createTestSignUpRequest("dup@email.com", "010-1111-2222", "테스터");
		SignUpRequestDto otherRequest = createTestSignUpRequest("dup@email.com", "010-3333-4444", "테스터2");

		given()
			.contentType(ContentType.JSON)
			.body(testRequest)
		.when()
			.post("/api/users/signup")
		.then()
			.statusCode(200)
			.body("code", equalTo(200));

		given()
			.contentType(ContentType.JSON)
			.body(otherRequest)
		.when()
			.post("/api/users/signup")
		.then()
			.statusCode(409)
			.body("code", equalTo(409));
	}

	@Test
	@DisplayName("회원가입 실패 - 휴대폰 번호 중복이면 409 반환")
	void signUp_Fail_DuplicatePhone() {
		SignUpRequestDto testRequest = createTestSignUpRequest("test_phone_dup_1@email.com", "010-5555-6666", "테스터");
		SignUpRequestDto otherRequest = createTestSignUpRequest("test_phone_dup_2@email.com", "010-5555-6666", "테스터2");

		given()
			.contentType(ContentType.JSON)
			.body(testRequest)
		.when()
			.post("/api/users/signup")
		.then()
			.statusCode(200)
			.body("code", equalTo(200));

		given()
			.contentType(ContentType.JSON)
			.body(otherRequest)
		.when()
			.post("/api/users/signup")
		.then()
			.statusCode(409)
			.body("code", equalTo(409));
	}

	@Test
	@DisplayName("로그인 실패 - 존재하지 않는 이메일이면 404 반환")
	void login_Fail_UserNotFound() {
		LoginRequestDto request = createTestLoginRequest("missing@email.com", "Abcd1234!");

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
		String email = "test_wrongpw@email.com";

		SignUpRequestDto signUpRequest = createTestSignUpRequest(email, "010-7777-6666", "테스터");
		LoginRequestDto wrongLoginRequest = createTestLoginRequest(email, "Wrong1234!");

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

	@Test
	@DisplayName("내 정보 조회 성공 - 로그인 후 accessToken으로 /me 호출하면 principal(email)이 주입된다")
	void me_Success_AfterLogin() {
		String email = "test_me@email.com";

		SignUpRequestDto signUpRequest = createTestSignUpRequest(email, "010-7777-6666", "테스터");
		LoginRequestDto loginRequest = createTestLoginRequest(email, "Abcd1234!");

		given()
			.contentType(ContentType.JSON)
			.body(signUpRequest)
		.when()
			.post("/api/users/signup")
		.then()
			.statusCode(200)
			.body("code", equalTo(200));

		String accessToken =
			given()
				.contentType(ContentType.JSON)
				.body(loginRequest)
			.when()
				.post("/api/users/login")
			.then()
				.statusCode(200)
				.body("code", equalTo(200))
				.body("message", equalTo("로그인 성공"))
				.body("data.accessToken", notNullValue())
				.extract()
				.path("data.accessToken");

		given()
			.header("Authorization", "Bearer " + accessToken)
		.when()
			.get("/api/users/me")
		.then()
			.statusCode(200)
			.body("code", equalTo(200))
			.body("message", equalTo("사용자 정보 조회 성공"))
			.body("data.email", equalTo(email))
			.body("data.name", equalTo("테스터"))
			.body("data.userRole", equalTo("CUSTOMER"))
			.body("data.userId", notNullValue());
	}

	@Test
	@DisplayName("내 정보 조회 실패 - 토큰 없이 요청하면 401")
	void me_Fail_WithoutToken() {
		given()
		.when()
			.get("/api/users/me")
		.then()
			.statusCode(401);
	}

	private SignUpRequestDto createTestSignUpRequest(String email, String phone, String name) {
		return new SignUpRequestDto(email, "Abcd1234!", name, phone, "CUSTOMER");
	}

	private LoginRequestDto createTestLoginRequest(String email, String password) {
		return new LoginRequestDto(email, password);
	}
}
