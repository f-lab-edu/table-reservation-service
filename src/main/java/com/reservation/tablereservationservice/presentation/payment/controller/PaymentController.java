package com.reservation.tablereservationservice.presentation.payment.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reservation.tablereservationservice.application.payment.service.PaymentService;
import com.reservation.tablereservationservice.global.annotation.CustomerOnly;
import com.reservation.tablereservationservice.global.annotation.LoginUser;
import com.reservation.tablereservationservice.global.common.CurrentUser;
import com.reservation.tablereservationservice.presentation.common.ApiResponse;
import com.reservation.tablereservationservice.presentation.payment.dto.PaymentConfirmRequestDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

	private final PaymentService paymentService;

	@CustomerOnly
	@PostMapping("/confirm")
	public ApiResponse<Void> confirm(@Valid @RequestBody PaymentConfirmRequestDto requestDto, @LoginUser CurrentUser user) {
		paymentService.approve(user.userId(), requestDto);
		return ApiResponse.success("예약 확정 중입니다.");
	}
}
