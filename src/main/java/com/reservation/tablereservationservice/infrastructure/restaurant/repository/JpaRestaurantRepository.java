package com.reservation.tablereservationservice.infrastructure.restaurant.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.restaurant.Restaurant;
import com.reservation.tablereservationservice.domain.restaurant.RestaurantRepository;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantEntity;
import com.reservation.tablereservationservice.infrastructure.restaurant.mapper.RestaurantMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaRestaurantRepository implements RestaurantRepository {

	private final RestaurantEntityRepository restaurantEntityRepository;

	@Override
	public Optional<Restaurant> findById(Long restaurantId) {
		return restaurantEntityRepository.findById(restaurantId)
			.map(RestaurantMapper.INSTANCE::toDomain);
	}

	@Override
	public Restaurant save(Restaurant restaurant) {
		RestaurantEntity entity = RestaurantMapper.INSTANCE.toEntity(restaurant);
		RestaurantEntity saved = restaurantEntityRepository.save(entity);

		return RestaurantMapper.INSTANCE.toDomain(saved);
	}

	@Override
	public void deleteAll() {
		restaurantEntityRepository.deleteAll();
	}
}
