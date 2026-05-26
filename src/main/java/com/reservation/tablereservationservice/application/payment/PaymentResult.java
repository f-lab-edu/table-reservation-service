package com.reservation.tablereservationservice.application.payment;

import java.time.OffsetDateTime;

import com.reservation.tablereservationservice.domain.payment.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResult {

	private String paymentKey;
	private String orderId;
	private Integer totalAmount;
	private OffsetDateTime approvedAt;
	private String status;

	public boolean isDone() {
		return PaymentStatus.DONE.name().equals(status);
	}
}
