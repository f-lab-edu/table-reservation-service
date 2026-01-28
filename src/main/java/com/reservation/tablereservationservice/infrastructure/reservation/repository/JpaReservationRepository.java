package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.global.exception.ErrorCode;
import com.reservation.tablereservationservice.global.exception.ReservationException;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.ReservationEntity;
import com.reservation.tablereservationservice.infrastructure.reservation.mapper.ReservationMapper;

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
	public boolean existsByUserIdAndVisitAtAndStatus(Long userId, LocalDateTime visitAt,
		ReservationStatus reservationStatus) {
		return reservationEntityRepository.existsByUserIdAndVisitAtAndStatus(userId, visitAt, reservationStatus);
	}

	@Override
	public Optional<Reservation> findById(Long reservationId) {
		return reservationEntityRepository.findById(reservationId)
			.map(ReservationMapper.INSTANCE::toDomain);
	}

	@Override
	public Reservation fetchById(Long reservationId) {
		return findById(reservationId)
			.orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Reservation"));
	}

	@Override
	public void deleteAll() {
		reservationEntityRepository.deleteAll();
	}
}
