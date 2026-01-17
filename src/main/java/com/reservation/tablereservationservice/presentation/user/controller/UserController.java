package com.reservation.tablereservationservice.presentation.user.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reservation.tablereservationservice.application.user.service.UserService;
import com.reservation.tablereservationservice.domain.user.User;
import com.reservation.tablereservationservice.presentation.common.ApiResponse;
import com.reservation.tablereservationservice.presentation.user.dto.LoginRequestDto;
import com.reservation.tablereservationservice.presentation.user.dto.LoginResponseDto;
import com.reservation.tablereservationservice.presentation.user.dto.LoginUserResponseDto;
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

		return ApiResponse.success("회원 가입 성공", responseDto);
	}

	@PostMapping("/login")
	public ApiResponse<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
		LoginResponseDto responseDto = userService.login(loginRequestDto.getEmail(), loginRequestDto.getPassword());

		return ApiResponse.success("로그인 성공", responseDto);
	}

	@GetMapping("/me")
	public ApiResponse<LoginUserResponseDto> getLoginUser(Authentication authentication) {
		String email = (String)authentication.getPrincipal();
		LoginUserResponseDto responseDto = userService.getCurrentUser(email);

		return ApiResponse.success("사용자 정보 조회 성공", responseDto);
	}
}
