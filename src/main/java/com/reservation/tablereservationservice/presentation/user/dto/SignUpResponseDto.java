package com.reservation.tablereservationservice.presentation.user.dto;

import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRole;

import lombok.Builder;
import lombok.Getter;

@Getter
public class SignUpResponseDto {

	private final Long userId;
	private final String email;
	private final String name;
	private final String phone;
	private final UserRole userRole;

	@Builder
	public SignUpResponseDto(Long userId, String email, String name, String phone, UserRole userRole) {
		this.userId = userId;
		this.email = email;
		this.name = name;
		this.phone = phone;
		this.userRole = userRole;
	}

	public static SignUpResponseDto from(User user) {
		return SignUpResponseDto.builder()
			.userId(user.getUserId())
			.email(user.getEmail())
			.name(user.getName())
			.phone(user.getPhone())
			.userRole(user.getUserRole())
			.build();
	}
}
