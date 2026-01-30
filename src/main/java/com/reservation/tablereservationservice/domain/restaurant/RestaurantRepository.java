package com.reservation.tablereservationservice.domain.restaurant;

import java.util.List;
import java.util.Optional;

public interface RestaurantRepository {

	Optional<Restaurant> findById(Long restaurantId);

	List<Long> findRestaurantIdsByOwnerId(Long ownerId);

	Restaurant save(Restaurant restaurant);

	void deleteAll();
}
