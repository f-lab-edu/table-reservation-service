package com.reservation.tablereservationservice.application.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.reservation.tablereservationservice.application.user.dto.LoginResultDto;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.UserException;
import com.reservation.tablereservationservice.global.jwt.JwtProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;

	public User signUp(User user) {
		validateDuplicateEmail(user.getEmail());
		validateDuplicatePhone(user.getPhone());

		String encodedPassword = passwordEncoder.encode(user.getPassword());
		user.encryptPassword(encodedPassword);

		return userRepository.save(user);
	}

	public LoginResultDto login(String email, String password) {
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new UserException(ErrorCode.USER_NOT_FOUND));

		if (!passwordEncoder.matches(password, user.getPassword())) {
			throw new UserException(ErrorCode.INVALID_PASSWORD);
		}

		String accessToken = jwtProvider.createAccessToken(user.getEmail(), user.getUserRole());

		return LoginResultDto.builder()
			.email(user.getEmail())
			.accessToken(accessToken)
			.userRole(user.getUserRole().name())
			.build();
	}

	private void validateDuplicateEmail(String email) {
		if (userRepository.existsByEmail(email)) {
			throw new UserException(ErrorCode.DUPLICATE_EMAIL);
		}
	}

	private void validateDuplicatePhone(String phone) {
		if (userRepository.existsByPhone(phone)) {
			throw new UserException(ErrorCode.DUPLICATE_PHONE);
		}
	}
}
