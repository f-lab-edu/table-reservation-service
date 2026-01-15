package com.reservation.tablereservationservice.presentation.user.dto;

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

}
