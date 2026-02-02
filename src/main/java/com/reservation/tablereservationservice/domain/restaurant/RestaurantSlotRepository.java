package com.reservation.tablereservationservice.domain.restaurant;

import java.util.List;
import java.util.Optional;

public interface RestaurantSlotRepository {

	Optional<RestaurantSlot> findById(Long slotId);

	RestaurantSlot save(RestaurantSlot restaurantSlot);

	RestaurantSlot fetchById(Long slotId);

	List<RestaurantSlot> findAllById(List<Long> slotIds);

	void deleteAll();

}
