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
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto;

public interface ReservationEntityRepository extends JpaRepository<ReservationEntity, Long> {

	boolean existsByUserIdAndVisitAtAndStatus(Long userId, LocalDateTime visitAt, ReservationStatus status);

	@Query("""
		select new com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto(
		    u.name,
			u.phone,
			r.note,
		    r.reservationId,
		    rt.restaurantId,
		    rt.name,
		    r.visitAt,
		    r.partySize,
		    r.status
		)
		from ReservationEntity r
		join RestaurantSlotEntity s
		    on s.slotId = r.slotId
		join RestaurantEntity rt
		    on rt.restaurantId = s.restaurantId
		join UserEntity u
		    on u.userId = r.userId
		where r.userId = :userId
		  and (:status is null or r.status = :status)
		  and r.visitAt >= :from
		  and r.visitAt < :to
		""")
	Page<ReservationListResponseDto> findMyReservationList(
		@Param("userId") Long userId,
		@Param("status") ReservationStatus status,
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to,
		Pageable pageable
	);

	@Query("""
		select new com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto(
		    u.name,
			u.phone,
		    r.note,
			r.reservationId,
		    rt.restaurantId,
		    rt.name,
		    r.visitAt,
		    r.partySize,
		    r.status
		)
		from ReservationEntity r
		join RestaurantSlotEntity s
		    on s.slotId = r.slotId
		join RestaurantEntity rt
		    on rt.restaurantId = s.restaurantId
		join UserEntity u
		    on u.userId = r.userId
		where s.restaurantId in :restaurantIds
		  and (:status is null or r.status = :status)
		  and r.visitAt >= :from
		  and r.visitAt < :to
		""")
	Page<ReservationListResponseDto> findOwnerReservations(
		@Param("restaurantIds") List<Long> restaurantIds,
		@Param("status") ReservationStatus status,
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to,
		Pageable pageable
	);
}
