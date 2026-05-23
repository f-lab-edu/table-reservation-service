package com.reservation.tablereservationservice.infrastructure.payment.repository;

import org.springframework.stereotype.Repository;

import com.reservation.tablereservationservice.domain.payment.Payment;
import com.reservation.tablereservationservice.domain.payment.PaymentRepository;
import com.reservation.tablereservationservice.infrastructure.payment.entity.PaymentEntity;
import com.reservation.tablereservationservice.infrastructure.payment.mapper.PaymentMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class JpaPaymentRepository implements PaymentRepository {

	private final PaymentEntityRepository paymentEntityRepository;

	@Override
	public Payment save(Payment payment) {
		PaymentEntity saved = paymentEntityRepository.save(PaymentMapper.INSTANCE.toEntity(payment));
		return PaymentMapper.INSTANCE.toDomain(saved);
	}
}
