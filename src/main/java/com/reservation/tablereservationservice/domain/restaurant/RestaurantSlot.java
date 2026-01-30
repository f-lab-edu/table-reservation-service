package com.reservation.tablereservationservice.domain.restaurant;

import java.time.LocalTime;

import lombok.Builder;
import lombok.Getter;

@Getter
public class RestaurantSlot {

	private Long slotId;
	private Long restaurantId;
	private LocalTime time;
	private Integer maxCapacity;

	@Builder
	public RestaurantSlot(Long slotId, Long restaurantId, LocalTime time, Integer maxCapacity) {
		this.slotId = slotId;
		this.restaurantId = restaurantId;
		this.time = time;
		this.maxCapacity = maxCapacity;
	}

	public boolean canAcceptPartySize(int partySize) {
		return partySize > 0 && partySize <= maxCapacity;
	}
}
