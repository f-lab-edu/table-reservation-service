package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
	public boolean existsByUserIdAndVisitAtAndStatus(
		Long userId,
		LocalDateTime visitAt,
		ReservationStatus reservationStatus
	) {
		return reservationEntityRepository.existsByUserIdAndVisitAtAndStatus(userId, visitAt, reservationStatus);
	}

	@Override
		public Page<Reservation> findMyReservations(
			Long userId,
			ReservationStatus status,
			LocalDateTime from,
			LocalDateTime to,
			Pageable pageable
		) {
			Page<ReservationEntity> page = (status == null)
				? reservationEntityRepository.findByUserIdAndVisitAtGreaterThanEqualAndVisitAtLessThan(userId, from, to, pageable)
				: reservationEntityRepository.findByUserIdAndStatusAndVisitAtGreaterThanEqualAndVisitAtLessThan(userId, status, from, to, pageable);

			return page.map(ReservationMapper.INSTANCE::toDomain);
		}

	@Override
	public Page<Reservation> findOwnerReservations(
		List<Long> restaurantIds,
		ReservationStatus status,
		LocalDateTime from,
		LocalDateTime to,
		Pageable pageable
	) {
		return reservationEntityRepository.findOwnerReservations(restaurantIds, status, from, to, pageable)
			.map(ReservationMapper.INSTANCE::toDomain);
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
	public void updateStatus(Reservation reservation) {
		ReservationEntity entity = reservationEntityRepository.findById(reservation.getReservationId())
			.orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Reservation"));

		entity.updateStatus(reservation.getStatus());
		// save() 호출 없음 -> 변경 감지 UPDATE
	}

	@Override
	public void deleteAll() {
		reservationEntityRepository.deleteAll();
	}
}
