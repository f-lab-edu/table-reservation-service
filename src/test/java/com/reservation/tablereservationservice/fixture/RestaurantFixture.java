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

	private Long restaurantId = 100L;
	private String name = "맛집A";
	private RegionCode regionCode = RegionCode.RG01;
	private CategoryCode categoryCode = CategoryCode.CT01;
	private String address = "서울 강남";
	private Long ownerId = 2L;

	public static RestaurantFixture restaurant() {
		return new RestaurantFixture();
	}

	public Restaurant build() {
		return Restaurant.builder()
			.restaurantId(restaurantId)
			.name(name)
			.regionCode(regionCode)
			.categoryCode(categoryCode)
			.address(address)
			.ownerId(ownerId)
			.build();
	}
}
