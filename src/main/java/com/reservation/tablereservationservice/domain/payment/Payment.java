package com.reservation.tablereservationservice.domain.payment;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
public class Payment {

	private Long paymentId;
	private Long reservationId;
	private String idempotencyKey;
	private String paymentKey;
	private Integer amount;
	private PaymentStatus status;
	private LocalDateTime approvedAt;

	@Builder
	public Payment(
			Long paymentId,
			Long reservationId,
			String idempotencyKey,
			String paymentKey,
			Integer amount,
			PaymentStatus status,
			LocalDateTime approvedAt
	) {
		this.paymentId = paymentId;
		this.reservationId = reservationId;
		this.idempotencyKey = idempotencyKey;
		this.paymentKey = paymentKey;
		this.amount = amount;
		this.status = status;
		this.approvedAt = approvedAt;
	}
}
