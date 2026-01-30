package com.reservation.tablereservationservice.domain.reservation;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReservationRepository {

	Reservation save(Reservation reservation);

	boolean existsByUserIdAndVisitAtAndStatus(Long userId, LocalDateTime visitAt, ReservationStatus reservationStatus);

	Optional<Reservation> findById(Long reservationId);

	Reservation fetchById(Long reservationId);

	void updateStatus(Reservation reservation);

	void deleteAll();
}
