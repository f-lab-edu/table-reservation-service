package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDateTime;

import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationRepository;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;
import com.reservation.tablereservationservice.infrastructure.reservation.entity.ReservationEntity;
import com.reservation.tablereservationservice.infrastructure.reservation.mapper.ReservationMapper;
import com.reservation.tablereservationservice.infrastructure.restaurant.entity.RestaurantSlotEntity;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaReservationRepository implements ReservationRepository {

	private final ReservationEntityRepository reservationEntityRepository;
	private final EntityManager em;

	@Override
	public Reservation save(Reservation reservation) {
		ReservationEntity entity = ReservationMapper.INSTANCE.toEntity(reservation);

		entity.assignSlot(em.getReference(RestaurantSlotEntity.class, reservation.getSlotId()));

		ReservationEntity saved = reservationEntityRepository.save(entity);
		return ReservationMapper.INSTANCE.toDomain(saved);
	}

	@Override
	public boolean existsByUserIdAndVisitAtAndStatus(Long userId, LocalDateTime visitAt,
		ReservationStatus reservationStatus) {
		return reservationEntityRepository.existsByUserIdAndVisitAtAndStatus(userId, visitAt, reservationStatus);
	}

	@Override
	public void deleteAll() {
		reservationEntityRepository.deleteAll();
	}
}
