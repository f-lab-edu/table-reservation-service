package com.reservation.tablereservationservice.presentation.user.dto;

import com.reservation.tablereservationservice.application.user.dto.LoginResultDto;

import lombok.Builder;
import lombok.Getter;

@Getter
public class LoginResponseDto {

	private final String accessToken;

	@Builder
	public LoginResponseDto(String accessToken) {
		this.accessToken = accessToken;
	}

	public static LoginResponseDto from(LoginResultDto tokenDto) {
		return LoginResponseDto.builder()
			.accessToken(tokenDto.getAccessToken())
			.build();
	}
}
