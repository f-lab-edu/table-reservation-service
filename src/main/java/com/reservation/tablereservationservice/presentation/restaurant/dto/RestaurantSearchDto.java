package com.reservation.tablereservationservice.presentation.restaurant.dto;

import com.reservation.tablereservationservice.domain.restaurant.CategoryCode;
import com.reservation.tablereservationservice.domain.restaurant.RegionCode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantSearchDto {

	private RegionCode regionCode;
	private CategoryCode categoryCode;
	private Long cursor;
	private int size = 10;
}
