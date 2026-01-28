package com.reservation.tablereservationservice.domain.reservation;

import java.time.LocalDateTime;

public interface ReservationRepository {

	Reservation save(Reservation reservation);

	boolean existsByUserIdAndVisitAtAndStatus(Long userId, LocalDateTime visitAt, ReservationStatus reservationStatus);

	void deleteAll();
}
