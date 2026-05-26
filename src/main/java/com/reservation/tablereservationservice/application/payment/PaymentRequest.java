package com.reservation.tablereservationservice.application.payment;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentRequest {

	private final String paymentKey;
	private final String orderId;
	private final Integer amount;

	public static PaymentRequest of(String paymentKey, String orderId, Integer amount) {
		return new PaymentRequest(paymentKey, orderId, amount);
	}
}
