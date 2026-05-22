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
	private Integer depositPerPerson;

	@Builder
	public RestaurantSlot(Long slotId, Long restaurantId, LocalTime time, Integer maxCapacity, Integer depositPerPerson) {
		this.slotId = slotId;
		this.restaurantId = restaurantId;
		this.time = time;
		this.maxCapacity = maxCapacity;
		this.depositPerPerson = depositPerPerson;
	}

	public int depositAmount(int partySize) {
		return depositPerPerson * partySize;
	}

	public boolean canAcceptPartySize(int partySize) {
		return partySize > 0 && partySize <= maxCapacity;
	}
}
