package com.reservation.tablereservationservice.application.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRepository;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.UserException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public User signUp(User user) {
		validateDuplicateEmail(user.getEmail());
		validateDuplicatePhone(user.getPhone());

		String encodedPassword = passwordEncoder.encode(user.getPassword());
		user.encryptPassword(encodedPassword);

		return userRepository.save(user);
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
