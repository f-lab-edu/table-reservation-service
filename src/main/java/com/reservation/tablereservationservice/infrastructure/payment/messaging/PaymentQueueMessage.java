package com.reservation.tablereservationservice.infrastructure.payment.messaging;

import java.time.LocalDateTime;

import com.reservation.tablereservationservice.application.payment.PaymentResult;
import com.reservation.tablereservationservice.domain.reservation.Reservation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PaymentQueueMessage {

	private Long reservationId;
	private String idempotencyKey;
	private String paymentKey;
	private Integer amount;
	private LocalDateTime approvedAt;

	public static PaymentQueueMessage of(Reservation reservation, String orderId, PaymentResult result) {
		PaymentQueueMessage message = new PaymentQueueMessage();
		message.reservationId = reservation.getReservationId();
		message.idempotencyKey = orderId;
		message.paymentKey = result.getPaymentKey();
		message.amount = result.getTotalAmount();
		message.approvedAt = result.getApprovedAt().toLocalDateTime();
		return message;
	}
}
