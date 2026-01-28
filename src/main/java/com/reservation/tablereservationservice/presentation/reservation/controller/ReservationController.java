package com.reservation.tablereservationservice.presentation.reservation.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.presentation.common.ApiResponse;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reservations")
public class ReservationController {

	private final ReservationService reservationService;

	@PreAuthorize("hasRole('CUSTOMER')")
	@PostMapping
	public ApiResponse<ReservationResponseDto> create(@Valid @RequestBody ReservationRequestDto requestDto,
		Authentication authentication) {
		String email = (String)authentication.getPrincipal();
		Reservation reservation = reservationService.create(email, requestDto);
		ReservationResponseDto responseDto = ReservationResponseDto.from(reservation);

		return ApiResponse.success("예약 요청 성공", responseDto);
	}

	@PreAuthorize("hasRole('CUSTOMER')")
	@PostMapping("/{reservationId}/cancel")
	public ApiResponse<ReservationResponseDto> cancel(@PathVariable Long reservationId, Authentication authentication) {
		String email = (String)authentication.getPrincipal();
		Reservation reservation = reservationService.cancel(email, reservationId);
		ReservationResponseDto responseDto = ReservationResponseDto.from(reservation);

		return ApiResponse.success("예약 취소 성공", responseDto);
	}

}
