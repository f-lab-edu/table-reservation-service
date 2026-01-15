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
		User user = createTestUser("test@email.com", "password123!", "홍길동", "010-1234-5678", UserRole.CUSTOMER);
		String encodedPassword = "encodedPassword";

		given(userRepository.existsByEmail(user.getEmail())).willReturn(false);
		given(userRepository.existsByPhone(user.getPhone())).willReturn(false);
		given(passwordEncoder.encode(anyString())).willReturn(encodedPassword);
		given(userRepository.save(any(User.class))).willReturn(user);

		// when
		User result = userService.signUp(user);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getEmail()).isEqualTo(user.getEmail());
		assertThat(result.getPassword()).isEqualTo(encodedPassword); // 암호화 확인
		assertThat(result.getName()).isEqualTo(user.getName());
		assertThat(result.getPhone()).isEqualTo(user.getPhone());
		assertThat(result.getUserRole()).isEqualTo(user.getUserRole());
	}

	@Test
	@DisplayName("회원가입 실패 - 이메일 중복")
	void signUp_Fail_DuplicateEmail() {
		// given
		User user = createTestUser("duplicate@email.com", "password123!", "홍길동", "010-1234-5678", UserRole.CUSTOMER);
		given(userRepository.existsByEmail(user.getEmail())).willReturn(true);

		// when & then
		assertThatThrownBy(() -> userService.signUp(user))
			.isInstanceOf(UserException.class)
			.satisfies(ex -> {
				UserException ue = (UserException)ex;
				assertThat(ue.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
				assertThat(ue.getArgs()).containsExactly("email");
			});

		verify(userRepository, never()).existsByPhone(anyString());
		verify(passwordEncoder, never()).encode(anyString());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("회원가입 실패 - 전화번호 중복")
	void signUp_Fail_DuplicatePhone() {
		// given
		User user = createTestUser("test@email.com", "password123!", "홍길동", "010-0000-0000", UserRole.CUSTOMER);
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

		verify(passwordEncoder, never()).encode(anyString());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("로그인 성공")
	void login_Success() {
		// given
		String email = "test@email.com";
		String rawPassword = "password123!";
		String encodedPassword = "encodedPassword";
		String token = "access.token.value";

		User user = createTestUser(email, encodedPassword, "홍길동", "010-1234-5678", UserRole.CUSTOMER);

		given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
		given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(true);
		given(jwtProvider.createAccessToken(email, UserRole.CUSTOMER)).willReturn(token);

		// when
		LoginResponseDto result = userService.login(email, rawPassword);

		// then
		assertThat(result).isNotNull();
		assertThat(result.getEmail()).isEqualTo(email);
		assertThat(result.getAccessToken()).isEqualTo(token);
		assertThat(result.getUserRole()).isEqualTo(UserRole.CUSTOMER.name());

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
			// 실제로 발생한 예외를 테스트
			.satisfies(ex -> {
				UserException ue = (UserException)ex;
				assertThat(ue.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
			});

		verify(passwordEncoder, never()).matches(anyString(), anyString());
		verify(jwtProvider, never()).createAccessToken(anyString(), any());
	}

	@Test
	@DisplayName("로그인 실패 - 비밀번호 불일치")
	void login_Fail_InvalidPassword() {
		// given
		String email = "test@email.com";
		String rawPassword = "wrongPassword!";
		String encodedPassword = "encodedPassword";

		User user = createTestUser(email, encodedPassword, "홍길동", "010-1234-5678", UserRole.CUSTOMER);

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

		verify(jwtProvider, never()).createAccessToken(anyString(), any());
	}

	private User createTestUser(String email, String password, String name, String phone, UserRole role) {
		return User.builder()
			.email(email)
			.password(password)
			.name(name)
			.phone(phone)
			.userRole(role)
			.build();
	}
}
