package com.reservation.tablereservationservice.application.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.domain.user.UserRole;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.UserException;
import com.reservation.tablereservationservice.global.jwt.JwtProvider;
import com.reservation.tablereservationservice.presentation.user.dto.LoginResponseDto;
import com.reservation.tablereservationservice.presentation.user.dto.LoginUserResponseDto;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtProvider jwtProvider;

	@InjectMocks
	private UserService userService;

	@Test
	@DisplayName("회원가입 성공")
	void signUp_Success() {
		// given
		User user = createTestUser("test@email.com", "테스터", "010-1234-5678");
		String encodedPassword = "encodedPassword";

		given(userRepository.existsByEmail(user.getEmail())).willReturn(false);
		given(userRepository.existsByPhone(user.getPhone())).willReturn(false);
		given(passwordEncoder.encode(anyString())).willReturn(encodedPassword);
		given(userRepository.save(any(User.class))).willReturn(user);

		// when
		User result = userService.signUp(user);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getEmail()).isEqualTo("test@email.com");
		assertThat(result.getName()).isEqualTo("테스터");
		assertThat(result.getPhone()).isEqualTo("010-1234-5678");
		assertThat(result.getUserRole()).isEqualTo(UserRole.CUSTOMER);
		assertThat(result.getPassword()).isEqualTo(encodedPassword);
	}

	@Test
	@DisplayName("회원가입 실패 - 이메일 중복")
	void signUp_Fail_DuplicateEmail() {
		// given
		User user = createTestUser("dup@email.com", "테스터", "010-1234-5678");
		given(userRepository.existsByEmail(user.getEmail())).willReturn(true);

		// when & then
		assertThatThrownBy(() -> userService.signUp(user))
			.isInstanceOf(UserException.class)
			.satisfies(ex -> {
				UserException ue = (UserException)ex;
				assertThat(ue.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
				assertThat(ue.getArgs()).containsExactly("email");
			});
	}

	@Test
	@DisplayName("회원가입 실패 - 전화번호 중복")
	void signUp_Fail_DuplicatePhone() {
		// given
		User user = createTestUser("test@email.com", "테스터", "010-0000-0000");

		given(userRepository.existsByEmail(user.getEmail())).willReturn(false);
		given(userRepository.existsByPhone(user.getPhone())).willReturn(true);

		// when & then
		assertThatThrownBy(() -> userService.signUp(user))
			.isInstanceOf(UserException.class)
			.satisfies(ex -> {
				UserException ue = (UserException)ex;
				assertThat(ue.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
				assertThat(ue.getArgs()).containsExactly("phone");
			});
	}

	@Test
	@DisplayName("로그인 성공")
	void login_Success() {
		// given
		String email = "login@email.com";
		String rawPassword = "password123!";
		String encodedPassword = "encodedPassword";
		String token = "access.token.value";

		User user = createTestUser(email, "테스터", "010-1111-2222");
		user.encryptPassword(encodedPassword);

		given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
		given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(true);
		given(jwtProvider.createAccessToken(email, UserRole.CUSTOMER)).willReturn(token);

		// when
		LoginResponseDto result = userService.login(email, rawPassword);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getEmail()).isEqualTo(email);
		assertThat(result.getUserRole()).isEqualTo(UserRole.CUSTOMER.name());
		assertThat(result.getAccessToken()).isEqualTo(token);
	}

	@Test
	@DisplayName("로그인 실패 - 존재하지 않는 이메일")
	void login_Fail_UserNotFound() {
		// given
		String email = "notfound@email.com";
		String rawPassword = "password123!";

		given(userRepository.findByEmail(email)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> userService.login(email, rawPassword))
			.isInstanceOf(UserException.class)
			.satisfies(ex -> {
				UserException ue = (UserException)ex;
				assertThat(ue.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
				assertThat(ue.getArgs()).containsExactly("User");
			});
	}

	@Test
	@DisplayName("로그인 실패 - 비밀번호 불일치")
	void login_Fail_InvalidPassword() {
		// given
		String email = "login_fail@email.com";
		String rawPassword = "wrongPassword!";
		String encodedPassword = "encodedPassword";

		User user = createTestUser(email, "테스터", "010-2222-3333");
		user.encryptPassword(encodedPassword);

		given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
		given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(false);

		// when & then
		assertThatThrownBy(() -> userService.login(email, rawPassword))
			.isInstanceOf(UserException.class)
			// 실제로 발생한 예외를 테스트
			.satisfies(ex -> {
				UserException ue = (UserException)ex;
				assertThat(ue.getErrorCode()).isEqualTo(ErrorCode.INVALID_PASSWORD);
			});
	}

	@Test
	@DisplayName("이메일로 현재 사용자 정보 조회 성공")
	void getCurrentUser_Success() {
		// given
		String email = "me@email.com";
		User user = createTestUser(email, "테스터", "010-4444-5555");

		given(userRepository.findByEmail(email)).willReturn(Optional.of(user));

		// when
		LoginUserResponseDto result = userService.getCurrentUser(email);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getEmail()).isEqualTo(email);
		assertThat(result.getName()).isEqualTo("테스터");
		assertThat(result.getUserRole()).isEqualTo(UserRole.CUSTOMER.name());
	}

	@Test
	@DisplayName("이메일로 현재 사용자 정보 조회 실패 - 사용자 없음")
	void getCurrentUser_Fail_UserNotFound() {
		// given
		String email = "notfound@email.com";
		given(userRepository.findByEmail(email)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> userService.getCurrentUser(email))
			.isInstanceOf(UserException.class)
			.satisfies(ex -> {
				UserException ue = (UserException)ex;
				assertThat(ue.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
				assertThat(ue.getArgs()).containsExactly("User");
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
