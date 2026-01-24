package com.reservation.tablereservationservice.domain.restaurant;

import java.util.Optional;

public interface RestaurantSlotRepository {

	Optional<RestaurantSlot> findById(Long slotId);

	RestaurantSlot save(RestaurantSlot restaurantSlot);

	void deleteAll();

}
