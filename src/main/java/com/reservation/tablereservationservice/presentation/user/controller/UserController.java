package com.reservation.tablereservationservice.presentation.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reservation.tablereservationservice.application.user.service.UserService;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.presentation.common.ApiResponse;
import com.reservation.tablereservationservice.presentation.user.dto.SignUpRequestDto;
import com.reservation.tablereservationservice.presentation.user.dto.SignUpResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	@PostMapping("/signup")
	public ApiResponse<SignUpResponseDto> signUp(@Valid @RequestBody SignUpRequestDto signUpRequestDto) {
		User user = userService.signUp(signUpRequestDto.toDomain());
		SignUpResponseDto responseDto = SignUpResponseDto.from(user);

		return ApiResponse.success(HttpStatus.CREATED, "회원 가입 성공", responseDto);
	}
}
