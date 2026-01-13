package com.reservation.tablereservationservice.presentation.user.dto;

import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.domain.user.UserRole;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SignUpResponseDto {

	private Long userId;
	private String email;
	private String name;
	private String phone;
	private UserRole userRole;

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
