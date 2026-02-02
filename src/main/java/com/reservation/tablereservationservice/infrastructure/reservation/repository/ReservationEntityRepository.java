package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.ReservationEntity;

public interface ReservationEntityRepository extends JpaRepository<ReservationEntity, Long> {

	boolean existsByUserIdAndVisitAtAndStatus(Long userId, LocalDateTime visitAt, ReservationStatus status);

	Page<ReservationEntity> findByUserIdAndVisitAtGreaterThanEqualAndVisitAtLessThan(
		Long userId,
		LocalDateTime from,
		LocalDateTime to,
		Pageable pageable
	);

	Page<ReservationEntity> findByUserIdAndStatusAndVisitAtGreaterThanEqualAndVisitAtLessThan(
		Long userId,
		ReservationStatus status,
		LocalDateTime from,
		LocalDateTime to,
		Pageable pageable
	);

	@Query("""
			select r
			from ReservationEntity r
			join RestaurantSlotEntity s on s.slotId = r.slotId
			where s.restaurantId in :restaurantIds
			  and (:status is null or r.status = :status)
			  and r.visitAt >= :from
			  and r.visitAt < :to
		""")
	Page<ReservationEntity> findOwnerReservations(
		@Param("restaurantIds") List<Long> restaurantIds,
		@Param("status") ReservationStatus status,
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to,
		Pageable pageable
	);
}
