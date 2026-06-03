package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
	public Optional<Reservation> findByIdempotencyKey(String idempotencyKey) {
		return reservationEntityRepository.findByIdempotencyKey(idempotencyKey)
				.map(ReservationMapper.INSTANCE::toDomain);
	}

	@Override
	public Reservation fetchByIdempotencyKey(String idempotencyKey) {
		return findByIdempotencyKey(idempotencyKey)
				.orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Reservation"));
	}

	@Override
	public boolean existsByUserIdAndVisitAtAndStatusIn(Long userId, LocalDateTime visitAt, List<ReservationStatus> statuses) {
		return reservationEntityRepository.existsByUserIdAndVisitAtAndStatusIn(userId, visitAt, statuses);
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
				: reservationEntityRepository.findByUserIdAndStatusAndVisitAtGreaterThanEqualAndVisitAtLessThan(
				userId,
				status,
				from,
				to,
				pageable
		);

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
	@Transactional
	public int updateStatusConditional(Long id, ReservationStatus from, ReservationStatus to) {
		if (!from.canTransitionTo(to)) {
			throw new ReservationException(ErrorCode.INVALID_STATUS_TRANSITION);
		}
		return reservationEntityRepository.updateStatusConditional(id, from, to);
	}

	@Override
	@Transactional
	public void updatePaymentKey(Reservation reservation) {
		ReservationEntity entity = reservationEntityRepository.findById(reservation.getReservationId())
				.orElseThrow(() -> new ReservationException(ErrorCode.RESOURCE_NOT_FOUND, "Reservation"));
		entity.updatePaymentKey(reservation.getPaymentKey());
	}

	@Override
	public List<Reservation> findPendingBefore(LocalDateTime createdBefore) {
		return reservationEntityRepository.findPendingBefore(createdBefore)
				.stream()
				.map(ReservationMapper.INSTANCE::toDomain)
				.toList();
	}

	@Override
	public List<Reservation> findCancelPendingBefore(LocalDateTime createdBefore) {
		return reservationEntityRepository.findCancelPendingBefore(createdBefore)
				.stream()
				.map(ReservationMapper.INSTANCE::toDomain)
				.toList();
	}

	@Override
	public void deleteAll() {
		reservationEntityRepository.deleteAll();
	}
}
