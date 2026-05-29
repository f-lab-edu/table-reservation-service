package com.reservation.tablereservationservice.infrastructure.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reservation.tablereservationservice.infrastructure.payment.entity.PaymentEntity;

import java.util.Optional;

public interface PaymentEntityRepository extends JpaRepository<PaymentEntity, Long> {

	Optional<PaymentEntity> findByReservationId(Long reservationId);
}
