package com.reservation.tablereservationservice.domain.reservation;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto;

public interface ReservationRepository {

	Reservation save(Reservation reservation);

	boolean existsByUserIdAndVisitAtAndStatus(Long userId, LocalDateTime visitAt, ReservationStatus reservationStatus);

	Page<ReservationListResponseDto> findMyReservations(
		Long userId, ReservationStatus status, LocalDateTime from,
		LocalDateTime to, Pageable pageable
	);

	Page<ReservationListResponseDto> findOwnerReservations(
		List<Long> restaurantIds, ReservationStatus status, LocalDateTime from,
		LocalDateTime to, Pageable pageable
	);

	void deleteAll();
}
