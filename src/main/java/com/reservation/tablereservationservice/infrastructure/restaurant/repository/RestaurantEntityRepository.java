package com.reservation.tablereservationservice.infrastructure.restaurant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reservation.tablereservationservice.domain.restaurant.CategoryCode;
import com.reservation.tablereservationservice.domain.restaurant.RegionCode;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantEntity;

public interface RestaurantEntityRepository extends JpaRepository<RestaurantEntity, Long> {

	Optional<RestaurantEntity> findById(Long restaurantId);

	List<RestaurantEntity> findAllByOwnerId(Long ownerId);

	@Query("""
			SELECT r FROM RestaurantEntity r
			WHERE (:regionCode IS NULL OR r.regionCode = :regionCode)
			  AND (:categoryCode IS NULL OR r.categoryCode = :categoryCode)
			  AND (:cursor IS NULL OR r.restaurantId > :cursor)
			ORDER BY r.restaurantId ASC
			""")
	List<RestaurantEntity> findByFilterWithCursor(
			@Param("regionCode") RegionCode regionCode,
			@Param("categoryCode") CategoryCode categoryCode,
			@Param("cursor") Long cursor,
			Pageable pageable);
}
