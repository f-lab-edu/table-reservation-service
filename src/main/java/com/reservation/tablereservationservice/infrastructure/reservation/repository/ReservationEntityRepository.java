package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.ReservationEntity;

public interface ReservationEntityRepository extends JpaRepository<ReservationEntity, Long> {

	Optional<ReservationEntity> findByIdempotencyKey(String idempotencyKey);

	boolean existsByUserIdAndVisitAtAndStatusIn(Long userId, LocalDateTime visitAt, Collection<ReservationStatus> statuses);

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

	@Query("select r from ReservationEntity r where r.status = 'PENDING' and r.createdAt < :createdBefore")
	List<ReservationEntity> findPendingBefore(@Param("createdBefore") LocalDateTime createdBefore);

	@Modifying
	@Query("UPDATE ReservationEntity r SET r.status = :to WHERE r.reservationId = :id AND r.status = :from")
	int updateStatusConditional(@Param("id") Long id, @Param("from") ReservationStatus from, @Param("to") ReservationStatus to);
}
