package com.reservation.tablereservationservice.domain.restaurant;

import lombok.Builder;
import lombok.Getter;

@Getter
public class Restaurant {

	private Long restaurantId;
	private Long ownerId;
	private String regionCodeId;
	private String categoryCodeId;
	private String name;
	private String address;
	private String description;
	private String mainMenuName;
	private Integer mainMenuPrice;

	@Builder
	public Restaurant(Long restaurantId, Long ownerId, String regionCodeId, String categoryCodeId, String name,
		String address, String description, String mainMenuName, Integer mainMenuPrice) {
		this.restaurantId = restaurantId;
		this.ownerId = ownerId;
		this.regionCodeId = regionCodeId;
		this.categoryCodeId = categoryCodeId;
		this.name = name;
		this.address = address;
		this.description = description;
		this.mainMenuName = mainMenuName;
		this.mainMenuPrice = mainMenuPrice;
	}
}
