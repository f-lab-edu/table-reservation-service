package com.reservation.tablereservationservice.infrastructure.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantSlotEntity;

public interface RestaurantSlotEntityRepository extends JpaRepository<RestaurantSlotEntity, Long> {

}
