package com.reservation.tablereservationservice.application.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
public class LoginResultDto {

	private String accessToken;

	@Builder
	public LoginResultDto(String accessToken) {
		this.accessToken = accessToken;
	}
}
