package com.reservation.tablereservationservice.infrastructure.reservation.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reservation.tablereservationservice.infrastructure.reservation.entity.ReservationEntity;

public interface ReservationEntityRepository extends JpaRepository<ReservationEntity, Long> {

	boolean existsByUserIdAndVisitAt(Long userId, LocalDateTime visitAt);
}
