package com.reservation.tablereservationservice.application.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

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

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserService userService;

	@Test
	@DisplayName("회원가입 성공")
	void signUp_Success() {
		// given
		User user = createTestUser("test@email.com", "010-1234-5678");
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

		// save 호출 1회 + 저장된 User의 비밀번호가 암호화된 상태인지 검증
		then(userRepository).should(times(1)).save(argThat(saved ->
	        saved.getPassword().equals(encodedPassword)
	    ));
	}

	@Test
	@DisplayName("회원가입 실패 - 이메일 중복")
	void signUp_Fail_DuplicateEmail() {
		// given
		User user = createTestUser("duplicate@email.com", "010-1234-5678");
		given(userRepository.existsByEmail(user.getEmail())).willReturn(true);

		// when & then
		assertThatThrownBy(() -> userService.signUp(user))
			.isInstanceOf(UserException.class)
			.hasMessage(ErrorCode.DUPLICATE_EMAIL.getMessage());

		verify(userRepository, never()).existsByPhone(anyString());
		verify(passwordEncoder, never()).encode(anyString());
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("회원가입 실패 - 전화번호 중복")
	void signUp_Fail_DuplicatePhone() {
		// given
		User user = createTestUser("test@email.com", "010-0000-0000");
		given(userRepository.existsByEmail(user.getEmail())).willReturn(false);
		given(userRepository.existsByPhone(user.getPhone())).willReturn(true);

		// when & then
		assertThatThrownBy(() -> userService.signUp(user))
			.isInstanceOf(UserException.class)
			.hasMessage(ErrorCode.DUPLICATE_PHONE.getMessage());

		verify(passwordEncoder, never()).encode(anyString());
		verify(userRepository, never()).save(any());
	}

	private User createTestUser(String email, String phone) {
		return User.builder()
			.email(email)
			.password("password123!")
			.name("홍길동")
			.phone(phone)
			.userRole(UserRole.CUSTOMER)
			.build();
	}
}