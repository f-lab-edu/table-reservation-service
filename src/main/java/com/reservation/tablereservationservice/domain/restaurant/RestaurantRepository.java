package com.reservation.tablereservationservice.domain.restaurant;

import java.util.List;
import java.util.Optional;

public interface RestaurantRepository {

	Optional<Restaurant> findById(Long restaurantId);

	List<Restaurant> findAllByOwnerId(Long ownerId);

	List<Restaurant> findAllById(List<Long> restaurantId);

	List<Restaurant> findByFilterWithCursor(RegionCode regionCode, CategoryCode categoryCode, Long cursor, int size);

	Restaurant save(Restaurant restaurant);

	void deleteAll();
}
