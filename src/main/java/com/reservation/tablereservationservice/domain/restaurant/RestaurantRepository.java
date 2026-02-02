package com.reservation.tablereservationservice.domain.restaurant;

import java.util.List;
import java.util.Optional;

import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantEntity;

public interface RestaurantRepository {

	Optional<Restaurant> findById(Long restaurantId);

	List<Restaurant> findAllByOwnerId(Long ownerId);

	List<Restaurant> findAllById(List<Long> restaurantId);

	Restaurant save(Restaurant restaurant);

	void deleteAll();
}
