package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.ReservationEntity;
import com.reservation.tablereservationservice.infrastructure.reservation.mapper.ReservationMapper;
import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationListResponseDto;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaReservationRepository implements ReservationRepository {

	private final ReservationEntityRepository reservationEntityRepository;

	@Override
	public Reservation save(Reservation reservation) {
		ReservationEntity entity = ReservationMapper.INSTANCE.toEntity(reservation);

		ReservationEntity saved = reservationEntityRepository.save(entity);
		return ReservationMapper.INSTANCE.toDomain(saved);
	}

	@Override
	public boolean existsByUserIdAndVisitAtAndStatus(
		Long userId,
		LocalDateTime visitAt,
		ReservationStatus reservationStatus
	) {
		return reservationEntityRepository.existsByUserIdAndVisitAtAndStatus(userId, visitAt, reservationStatus);
	}

	@Override
	public Page<ReservationListResponseDto> findMyReservations(
		Long userId,
		ReservationStatus status,
		LocalDateTime from,
		LocalDateTime to,
		Pageable pageable
	) {
		return reservationEntityRepository.findMyReservationList(userId, status, from, to, pageable);
	}

	@Override
	public Page<ReservationListResponseDto> findOwnerReservations(
		List<Long> restaurantIds,
		ReservationStatus status,
		LocalDateTime from,
		LocalDateTime to,
		Pageable pageable
	) {
		return reservationEntityRepository.findOwnerReservations(restaurantIds, status, from, to, pageable);
	}

	@Override
	public void deleteAll() {
		reservationEntityRepository.deleteAll();
	}
}
