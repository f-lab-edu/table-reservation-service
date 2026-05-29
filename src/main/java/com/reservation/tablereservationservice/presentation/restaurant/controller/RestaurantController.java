package com.reservation.tablereservationservice.presentation.restaurant.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reservation.tablereservationservice.application.restaurant.RestaurantService;
import com.reservation.tablereservationservice.presentation.common.ApiResponse;
import com.reservation.tablereservationservice.presentation.restaurant.dto.RestaurantCursorResponseDto;
import com.reservation.tablereservationservice.presentation.restaurant.dto.RestaurantSearchDto;
import com.reservation.tablereservationservice.presentation.restaurant.dto.RestaurantSlotResponseDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/restaurants")
public class RestaurantController {

	private final RestaurantService restaurantService;

	@GetMapping
	public ApiResponse<RestaurantCursorResponseDto> findRestaurants(@ModelAttribute RestaurantSearchDto searchDto) {
		RestaurantCursorResponseDto responseDto = restaurantService.findRestaurants(searchDto);
		return ApiResponse.success("매장 목록 조회 성공", responseDto);
	}

	@GetMapping("/{restaurantId}/slots")
	public ApiResponse<List<RestaurantSlotResponseDto>> findAvailableSlots(
			@PathVariable Long restaurantId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
	) {
		List<RestaurantSlotResponseDto> responseDto = restaurantService.findAvailableSlots(restaurantId, date);
		return ApiResponse.success("슬롯 조회 성공", responseDto);
	}
}
