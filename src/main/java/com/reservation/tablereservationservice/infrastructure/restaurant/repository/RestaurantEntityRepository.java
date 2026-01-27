package com.reservation.tablereservationservice.infrastructure.restaurant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantEntity;

public interface RestaurantEntityRepository extends JpaRepository<RestaurantEntity, Long> {

	Optional<RestaurantEntity> findById(Long restaurantId);

	@Query("""
			select r.restaurantId
			from RestaurantEntity r
			where r.ownerId = :ownerId
		""")
	List<Long> findRestaurantIdsByOwnerId(@Param("ownerId") Long ownerId);
}
