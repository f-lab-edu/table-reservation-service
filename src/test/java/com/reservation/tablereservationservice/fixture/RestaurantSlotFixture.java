package com.reservation.tablereservationservice.fixture;

import java.time.LocalTime;

import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RestaurantSlotFixture {

	private Long slotId = 10L;
	private Long restaurantId = 100L;
	private LocalTime time = LocalTime.of(19, 0);
	private int maxCapacity = 10;

	public static RestaurantSlotFixture slot() {
		return new RestaurantSlotFixture();
	}

	public RestaurantSlot build() {
		return RestaurantSlot.builder()
			.slotId(slotId)
			.restaurantId(restaurantId)
			.time(time)
			.maxCapacity(maxCapacity)
			.build();
	}
}
