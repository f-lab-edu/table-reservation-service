package com.reservation.tablereservationservice.infrastructure.restaurant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.restaurant.CategoryCode;
import com.reservation.tablereservationservice.domain.restaurant.RegionCode;
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
	public List<Restaurant> findAllByOwnerId(Long ownerId) {
		return restaurantEntityRepository.findAllByOwnerId(ownerId).stream()
			.map(RestaurantMapper.INSTANCE::toDomain)
			.toList();
	}

	@Override
	public List<Restaurant> findAllById(List<Long> restaurantIds) {
		return restaurantEntityRepository.findAllById(restaurantIds).stream()
			.map(RestaurantMapper.INSTANCE::toDomain)
			.toList();
	}

	@Override
	public List<Restaurant> findByFilterWithCursor(RegionCode regionCode, CategoryCode categoryCode, Long cursor, int size) {
		return restaurantEntityRepository
				.findByFilterWithCursor(regionCode, categoryCode, cursor, PageRequest.of(0, size))
				.stream()
				.map(RestaurantMapper.INSTANCE::toDomain)
				.toList();
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
