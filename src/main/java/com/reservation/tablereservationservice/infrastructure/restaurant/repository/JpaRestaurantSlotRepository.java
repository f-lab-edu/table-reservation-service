package com.reservation.tablereservationservice.infrastructure.restaurant.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlot;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantSlotRepository;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.RestaurantException;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantSlotEntity;
import com.reservation.tablereservationservice.infrastructure.restaurant.mapper.RestaurantMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaRestaurantSlotRepository implements RestaurantSlotRepository {

	private final RestaurantSlotEntityRepository restaurantSlotEntityRepository;

	@Override
	public Optional<RestaurantSlot> findById(Long slotId) {
		return restaurantSlotEntityRepository.findById(slotId)
			.map(RestaurantMapper.INSTANCE::toDomain);
	}

	@Override
	public RestaurantSlot save(RestaurantSlot restaurantSlot) {
		RestaurantSlotEntity entity = RestaurantMapper.INSTANCE.toEntity(restaurantSlot);

		RestaurantSlotEntity saved = restaurantSlotEntityRepository.save(entity);
		return RestaurantMapper.INSTANCE.toDomain(saved);

	}

	@Override
	public RestaurantSlot fetchById(Long slotId) {
		return findById(slotId)
			.orElseThrow(() -> new RestaurantException(ErrorCode.RESOURCE_NOT_FOUND, "Restaurant"));
	}

	@Override
	public void deleteAll() {
		restaurantSlotEntityRepository.deleteAll();
	}
}
