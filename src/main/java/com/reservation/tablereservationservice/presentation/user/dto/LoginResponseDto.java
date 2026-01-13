package com.reservation.tablereservationservice.presentation.user.dto;

import com.reservation.tablereservationservice.application.user.dto.LoginResultDto;

import lombok.Builder;
import lombok.Getter;

@Getter
public class LoginResponseDto {

	private final String email;
	private final String userRole;
	private final String accessToken;

	@Builder
	public LoginResponseDto(String email, String userRole, String accessToken) {
		this.email = email;
		this.userRole = userRole;
		this.accessToken = accessToken;
	}

	public static LoginResponseDto from(LoginResultDto dto) {
		return LoginResponseDto.builder()
			.email(dto.getEmail())
			.userRole(dto.getUserRole())
			.accessToken(dto.getAccessToken())
			.build();
	}
}
