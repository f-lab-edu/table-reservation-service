package com.reservation.tablereservationservice.presentation.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginUserResponseDto {

	private Long userId;
	private String email;
	private String name;
	private String userRole;

}
