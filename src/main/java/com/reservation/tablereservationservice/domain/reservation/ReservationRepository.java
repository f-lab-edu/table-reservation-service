package com.reservation.tablereservationservice.domain.reservation;

import java.time.LocalDateTime;

public interface ReservationRepository {

	Reservation save(Reservation reservation);

	boolean existsByUserIdAndVisitAt(Long userId, LocalDateTime visitAt);
}
