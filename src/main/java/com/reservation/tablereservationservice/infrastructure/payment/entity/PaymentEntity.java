package com.reservation.tablereservationservice.infrastructure.payment.entity;

import java.time.LocalDateTime;

import com.reservation.tablereservationservice.domain.payment.PaymentStatus;
import com.reservation.tablereservationservice.infrastructure.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "payment")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PaymentEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long paymentId;

	@Column(nullable = false)
	private Long reservationId;

	@Column(unique = true, nullable = false, length = 36)
	private String idempotencyKey;

	@Column(nullable = false)
	private String paymentKey;

	@Column(nullable = false)
	private Integer amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PaymentStatus status;

	@Column(nullable = false)
	private LocalDateTime approvedAt;

	@Builder
	public PaymentEntity(
			Long reservationId,
			String idempotencyKey,
			String paymentKey,
			Integer amount,
			PaymentStatus status,
			LocalDateTime approvedAt
	) {
		this.reservationId = reservationId;
		this.idempotencyKey = idempotencyKey;
		this.paymentKey = paymentKey;
		this.amount = amount;
		this.status = status;
		this.approvedAt = approvedAt;
	}
}
