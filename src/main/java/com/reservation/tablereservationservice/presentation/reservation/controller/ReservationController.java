package com.reservation.tablereservationservice.presentation.reservation.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.global.annotation.CustomerOnly;
import com.reservation.tablereservationservice.global.annotation.LoginUser;
import com.reservation.tablereservationservice.global.annotation.OwnerOnly;
import com.reservation.tablereservationservice.global.common.CurrentUser;
import com.reservation.tablereservationservice.presentation.common.ApiResponse;
import com.reservation.tablereservationservice.presentation.common.PageResponseDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationResponseDto;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationSearchDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reservations")
public class ReservationController {

	private final ReservationService reservationService;

	@CustomerOnly
	@PostMapping
	public ApiResponse<ReservationResponseDto> create(
		@Valid @RequestBody ReservationRequestDto requestDto,
		@LoginUser CurrentUser user
	) {
		Reservation reservation = reservationService.create(user.email(), requestDto);
		ReservationResponseDto responseDto = ReservationResponseDto.from(reservation);

		return ApiResponse.success("예약 요청 성공", responseDto);
	}

	@CustomerOnly
	@GetMapping("/me")
	public ApiResponse<PageResponseDto<ReservationListResponseDto>> findMyReservations(
		@ModelAttribute ReservationSearchDto searchDto,
		@PageableDefault(page = 0, size = 10, sort = "visitAt", direction = Sort.Direction.DESC) Pageable pageable,
		@LoginUser CurrentUser user
	) {

		searchDto.setPageable(pageable);
		PageResponseDto<ReservationListResponseDto> responseDto = reservationService.findMyReservations(
			user.email(),
			searchDto
		);

		return ApiResponse.success("예약 조회 성공", responseDto);
	}

	@OwnerOnly
	@GetMapping("/owner")
	public ApiResponse<PageResponseDto<ReservationListResponseDto>> findOwnerReservations(
		@ModelAttribute ReservationSearchDto searchDto,
		@PageableDefault(page = 0, size = 10, sort = "visitAt", direction = Sort.Direction.DESC) Pageable pageable,
		@LoginUser CurrentUser user
	) {

		searchDto.setPageable(pageable);
		PageResponseDto<ReservationListResponseDto> responseDto = reservationService.findOwnerReservations(
			user.email(),
			searchDto
		);

		return ApiResponse.success("예약 조회 성공", responseDto);

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
