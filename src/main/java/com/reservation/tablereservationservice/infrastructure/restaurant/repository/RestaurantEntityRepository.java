package com.reservation.tablereservationservice.infrastructure.restaurant.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantEntity;

public interface RestaurantEntityRepository extends JpaRepository<RestaurantEntity, Long> {

	Optional<RestaurantEntity> findById(Long restaurantId);
}
