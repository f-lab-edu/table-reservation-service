package com.reservation.tablereservationservice.presentation.restaurant.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
public class RestaurantCursorResponseDto {

	private List<RestaurantResponseDto> content;
	private Long nextCursor;
	private boolean hasNext;

	@Builder
	public RestaurantCursorResponseDto(List<RestaurantResponseDto> content, Long nextCursor, boolean hasNext) {
		this.content = content;
		this.nextCursor = nextCursor;
		this.hasNext = hasNext;
	}
}
