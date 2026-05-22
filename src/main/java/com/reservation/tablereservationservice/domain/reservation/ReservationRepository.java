package com.reservation.tablereservationservice.domain.reservation;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface ReservationRepository {

	Reservation save(Reservation reservation);

	Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

	boolean existsByUserIdAndVisitAtAndStatusIn(Long userId, LocalDateTime visitAt, List<ReservationStatus> statuses);

	Page<Reservation> findMyReservations(
		Long userId,
		ReservationStatus status,
		LocalDateTime from,
		LocalDateTime to,
		Pageable pageable
	);

	Page<Reservation> findOwnerReservations(
		List<Long> restaurantIds,
		ReservationStatus status,
		LocalDateTime from,
		LocalDateTime to,
		Pageable pageable
	);

	Optional<Reservation> findById(Long reservationId);

	Reservation fetchById(Long reservationId);

	void updateStatus(Reservation reservation);

	void deleteAll();
}
