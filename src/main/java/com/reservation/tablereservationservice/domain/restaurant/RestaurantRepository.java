package com.reservation.tablereservationservice.domain.restaurant;

import java.util.Optional;

public interface RestaurantRepository {

	Optional<Restaurant> findById(Long restaurantId);

	Restaurant save(Restaurant restaurant);

	void deleteAll();
}
