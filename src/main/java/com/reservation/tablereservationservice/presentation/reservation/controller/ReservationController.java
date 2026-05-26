package com.reservation.tablereservationservice.presentation.reservation.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reservation.tablereservationservice.application.reservation.facade.ReservationFacade;
import com.reservation.tablereservationservice.application.reservation.facade.ReservationOptimisticFacade;
import com.reservation.tablereservationservice.application.reservation.service.ReservationCreateResult;
import com.reservation.tablereservationservice.application.reservation.service.ReservationService;
import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.global.annotation.CustomerOnly;
import com.reservation.tablereservationservice.global.annotation.LoginUser;
import com.reservation.tablereservationservice.global.annotation.OwnerOnly;
import com.reservation.tablereservationservice.global.common.CurrentUser;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
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
	private final ReservationFacade reservationFacade;
	private final ReservationOptimisticFacade reservationOptimisticFacade;

	@CustomerOnly
	@PostMapping
	public ApiResponse<ReservationResponseDto> create(
			@Valid @RequestBody ReservationRequestDto requestDto,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@LoginUser CurrentUser user
	) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new ReservationException(ErrorCode.MISSING_PARAMETER);
		}

		ReservationCreateResult result = reservationFacade.reserve(user.email(), requestDto, idempotencyKey);
		return ApiResponse.success("예약 접수 성공", ReservationResponseDto.from(result));
	}

	@CustomerOnly
	@GetMapping("/me")
	public ApiResponse<PageResponseDto<ReservationListResponseDto>> findMyReservations(
			@ModelAttribute ReservationSearchDto searchDto,
			@PageableDefault(page = 0, size = 10, sort = "visitAt", direction = Sort.Direction.DESC) Pageable pageable,
			@LoginUser CurrentUser user
	) {
		searchDto.setPageable(pageable);
		PageResponseDto<ReservationListResponseDto> responseDto = reservationService.findMyReservations(user.email(), searchDto);
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
		PageResponseDto<ReservationListResponseDto> responseDto = reservationService.findOwnerReservations(user.email(), searchDto);
		return ApiResponse.success("예약 조회 성공", responseDto);
	}

	@CustomerOnly
	@PostMapping("/{reservationId}/cancel")
	public ApiResponse<ReservationResponseDto> cancel(@PathVariable Long reservationId, @LoginUser CurrentUser user) {
		Reservation reservation = reservationOptimisticFacade.cancelWithRetry(user.email(), reservationId);
		return ApiResponse.success("예약 취소 성공", ReservationResponseDto.from(reservation));
	}
}
