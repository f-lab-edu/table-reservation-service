package com.reservation.tablereservationservice.application.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
public class LoginResultDto {

	private String email;
	private String userRole;
	private String accessToken;

	@Builder
	public LoginResultDto(String email, String userRole, String accessToken) {
		this.email = email;
		this.userRole = userRole;
		this.accessToken = accessToken;
	}
}
