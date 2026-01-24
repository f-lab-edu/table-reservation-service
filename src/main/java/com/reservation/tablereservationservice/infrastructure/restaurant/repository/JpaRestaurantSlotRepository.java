package com.reservation.tablereservationservice.infrastructure.restaurant.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantEntity;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantSlotEntity;
import com.reservation.tablereservationservice.infrastructure.restaurant.mapper.RestaurantMapper;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaRestaurantSlotRepository implements RestaurantSlotRepository {

	private final RestaurantSlotEntityRepository restaurantSlotEntityRepository;
	private final EntityManager em;

	@Override
	public Optional<RestaurantSlot> findById(Long slotId) {
		return restaurantSlotEntityRepository.findById(slotId)
			.map(RestaurantMapper.INSTANCE::toDomain);
	}

	@Override
	public RestaurantSlot save(RestaurantSlot restaurantSlot) {
		RestaurantSlotEntity entity = RestaurantMapper.INSTANCE.toEntity(restaurantSlot);

		entity.assignRestaurant(em.getReference(RestaurantEntity.class, restaurantSlot.getRestaurantId()));

		RestaurantSlotEntity saved = restaurantSlotEntityRepository.save(entity);
		return RestaurantMapper.INSTANCE.toDomain(saved);

	}

	@Override
	public void deleteAll() {
		restaurantSlotEntityRepository.deleteAll();
	}
}
