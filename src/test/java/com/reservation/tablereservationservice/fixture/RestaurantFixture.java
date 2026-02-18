package com.reservation.tablereservationservice.fixture;

import com.reservation.tablereservationservice.domain.restaurant.CategoryCode;
import com.reservation.tablereservationservice.domain.restaurant.RegionCode;
import com.reservation.tablereservationservice.domain.restaurant.Restaurant;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RestaurantFixture {

	private Long restaurantId = null;
	private String name = "강남 한상";
	private RegionCode regionCode = RegionCode.RG01;
	private CategoryCode categoryCode = CategoryCode.CT01;
	private String address = "서울 강남";
	private Long ownerId = 2L;

	public static RestaurantFixture restaurant() {
		return new RestaurantFixture();
	}

	public Restaurant build() {
		Restaurant.RestaurantBuilder builder = Restaurant.builder()
			.name(name)
			.regionCode(regionCode)
			.categoryCode(categoryCode)
			.address(address)
			.ownerId(ownerId);

		if (restaurantId != null) {
			builder.restaurantId(restaurantId);
		}

		return builder.build();
	}
}
