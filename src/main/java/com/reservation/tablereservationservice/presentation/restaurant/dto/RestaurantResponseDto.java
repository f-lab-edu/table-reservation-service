package com.reservation.tablereservationservice.presentation.restaurant.dto;

import com.reservation.tablereservationservice.domain.restaurant.CategoryCode;
import com.reservation.tablereservationservice.domain.restaurant.RegionCode;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;

import lombok.Builder;
import lombok.Getter;

@Getter
public class RestaurantResponseDto {

	private Long restaurantId;
	private String name;
	private RegionCode regionCode;
	private CategoryCode categoryCode;
	private String address;
	private String description;
	private String mainMenuName;
	private Integer mainMenuPrice;

	@Builder
	public RestaurantResponseDto(
			Long restaurantId,
			String name,
			RegionCode regionCode,
			CategoryCode categoryCode,
			String address,
			String description,
			String mainMenuName,
			Integer mainMenuPrice
	) {
		this.restaurantId = restaurantId;
		this.name = name;
		this.regionCode = regionCode;
		this.categoryCode = categoryCode;
		this.address = address;
		this.description = description;
		this.mainMenuName = mainMenuName;
		this.mainMenuPrice = mainMenuPrice;
	}

	public static RestaurantResponseDto from(Restaurant restaurant) {
		return RestaurantResponseDto.builder()
				.restaurantId(restaurant.getRestaurantId())
				.name(restaurant.getName())
				.regionCode(restaurant.getRegionCode())
				.categoryCode(restaurant.getCategoryCode())
				.address(restaurant.getAddress())
				.description(restaurant.getDescription())
				.mainMenuName(restaurant.getMainMenuName())
				.mainMenuPrice(restaurant.getMainMenuPrice())
				.build();
	}
}
