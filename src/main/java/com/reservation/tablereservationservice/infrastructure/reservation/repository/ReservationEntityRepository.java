package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.ReservationEntity;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto;

public interface ReservationEntityRepository extends JpaRepository<ReservationEntity, Long> {

	boolean existsByUserIdAndVisitAtAndStatus(Long userId, LocalDateTime visitAt, ReservationStatus status);

	@Query("""
		select new com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto(
			r.reservationId,
			rest.restaurantId,
			rest.name,
			r.visitAt,
			r.partySize,
			r.status
		)
		from ReservationEntity r, RestaurantSlotEntity slot, RestaurantEntity rest
		where r.slotId = slot.slotId
		  and slot.restaurantId = rest.restaurantId
		  and r.userId = :userId
		  and r.visitAt between :from and :to
		  and (:status is null or r.status = :status)
		""")
	Page<ReservationListResponseDto> findMyReservationList(
		@Param("userId") Long userId,
		@Param("status") ReservationStatus status,
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to,
		Pageable pageable
	);
}
