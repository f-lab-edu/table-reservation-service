package com.reservation.tablereservationservice.domain.restaurant;

import lombok.Builder;
import lombok.Getter;

@Getter
public class Restaurant {

	private Long restaurantId;
	private Long ownerId;
	private RegionCode regionCode;
	private CategoryCode categoryCode;
	private String name;
	private String address;
	private String description;
	private String mainMenuName;
	private Integer mainMenuPrice;

	@Builder
	public Restaurant(Long restaurantId, Long ownerId, RegionCode regionCode, CategoryCode categoryCode, String name,
		String address, String description, String mainMenuName, Integer mainMenuPrice) {
		this.restaurantId = restaurantId;
		this.ownerId = ownerId;
		this.regionCode = regionCode;
		this.categoryCode = categoryCode;
		this.name = name;
		this.address = address;
		this.description = description;
		this.mainMenuName = mainMenuName;
		this.mainMenuPrice = mainMenuPrice;
	}
}
