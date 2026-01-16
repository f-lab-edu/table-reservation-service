package com.reservation.tablereservationservice.application.user.service;

import static org.assertj.core.api.AssertionsForClassTypes.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.domain.user.UserRole;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.UserException;
import com.reservation.tablereservationservice.presentation.user.dto.LoginResponseDto;
import com.reservation.tablereservationservice.presentation.user.dto.LoginUserResponseDto;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceIntegrationTest {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	@DisplayName("회원가입 성공 - DB 저장 및 비밀번호 암호화 확인")
	void signUp_Success_PersistsAndEncrypts() {
		// given
		User user = createTestUser("test@email.com", "테스터", "010-1234-5678");

		// when
		User saved = userService.signUp(user);

		// then
		assertThat(saved).isNotNull();
		assertThat(saved.getUserId()).isNotNull();
		assertThat(userRepository.findByEmail("test@email.com")).isPresent();
		assertThat(saved.getPassword()).isNotEqualTo("password123!");
		assertThat(passwordEncoder.matches("password123!", saved.getPassword())).isTrue();
	}

	@Test
	@DisplayName("회원가입 실패 - 이메일 중복이면 DUPLICATE_RESOURCE")
	void signUp_Fail_DuplicateEmail() {
		// given
		User user = createTestUser("duplicate@email.com", "테스터", "010-1234-5678");
		userService.signUp(user);

		User other = createTestUser("duplicate@email.com", "테스터2", "010-9999-8888");

		// when & then
		assertThatThrownBy(() -> userService.signUp(other))
			.isInstanceOf(UserException.class)
			.satisfies(ex -> {
				UserException ue = (UserException)ex;
				assertThat(ue.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
				assertThat(ue.getArgs()).containsExactly("email"); // 상세 메시지까지 확인
			});
	}

	@Test
	@DisplayName("회원가입 실패 - 전화번호 중복이면 DUPLICATE_RESOURCE")
	void signUp_Fail_DuplicatePhone() {
		// given
		User user = createTestUser("test@email.com", "테스터", "010-0000-0000");
		userService.signUp(user);

		User other = createTestUser("other@email.com", "테스터2", "010-0000-0000");

		// when & then
		assertThatThrownBy(() -> userService.signUp(other))
			.isInstanceOf(UserException.class)
			.satisfies(ex -> {
				UserException ue = (UserException)ex;
				assertThat(ue.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
				assertThat(ue.getArgs()).containsExactly("phone"); // 상세 메시지까지 확인
			});
	}

	@Test
	@DisplayName("로그인 성공 - 회원가입 후 로그인하면 토큰이 내려온다")
	void login_Success_AfterSignUp() {
		// given
		User user = createTestUser("login@email.com", "테스터", "010-9999-8888");
		userService.signUp(user);

		// when
		LoginResponseDto result = userService.login("login@email.com", "password123!");

		// then
		assertThat(result).isNotNull();
		assertThat(result.getEmail()).isEqualTo("login@email.com");
		assertThat(result.getUserRole()).isEqualTo(UserRole.CUSTOMER.name());
		assertThat(result.getAccessToken()).isNotNull();
		assertThat(result.getAccessToken()).isNotBlank();
	}

	@Test
	@DisplayName("로그인 실패 - 존재하지 않는 이메일이면 RESOURCE_NOT_FOUND")
	void login_Fail_UserNotFound() {
		// given
		String email = "notfound@email.com";
		String password = "password123!";

		// when & then
		assertThatThrownBy(() -> userService.login(email, password))
			.isInstanceOf(UserException.class)
			.satisfies(ex -> {
				UserException ue = (UserException)ex;
				assertThat(ue.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
				assertThat(ue.getArgs()).containsExactly("User"); // 상세 메시지까지 확인
			});
	}

	@Test
	@DisplayName("로그인 실패 - 비밀번호 불일치면 INVALID_PASSWORD")
	void login_Fail_InvalidPassword() {
		// given
		User user = createTestUser("wrongpw@email.com", "테스터", "010-7777-6666");
		userService.signUp(user);

		// when & then
		assertThatThrownBy(() -> userService.login("wrongpw@email.com", "wrongPassword!"))
			.isInstanceOf(UserException.class)
			.satisfies(ex -> {
				UserException ue = (UserException)ex;
				assertThat(ue.getErrorCode()).isEqualTo(ErrorCode.INVALID_PASSWORD);
			});
	}

	@Test
	@DisplayName("이메일로 현재 사용자 정보 조회 성공")
	void getCurrentUser_Success() {
		// given
		User saved = userService.signUp(createTestUser("me@email.com", "테스터", "010-1111-2222"));

		// when
		LoginUserResponseDto result = userService.getCurrentUser("me@email.com");

		// then
		assertThat(result).isNotNull();
		assertThat(result.getUserId()).isEqualTo(saved.getUserId());
		assertThat(result.getEmail()).isEqualTo("me@email.com");
		assertThat(result.getName()).isEqualTo("테스터");
		assertThat(result.getUserRole()).isEqualTo(UserRole.CUSTOMER.name());
	}

	@Test
	@DisplayName("이메일로 현재 사용자 정보 조회 실패 - 사용자 없음")
	void getCurrentUser_Fail_UserNotFound() {
		// given
		String email = "missing@email.com";

		// when & then
		assertThatThrownBy(() -> userService.getCurrentUser(email))
			.isInstanceOf(UserException.class)
			.satisfies(ex -> {
				UserException ue = (UserException)ex;
				assertThat(ue.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
				assertThat(ue.getArgs()).containsExactly("User"); // 상세 메시지까지 확인
			});
	}

	private User createTestUser(String email, String name, String phone) {
		return User.builder()
			.email(email)
			.password("password123!")
			.name(name)
			.phone(phone)
			.userRole(UserRole.CUSTOMER)
			.build();
	}
}
