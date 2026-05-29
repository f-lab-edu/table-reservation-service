package com.reservation.tablereservationservice.application.payment;

public interface PaymentClient {

	PaymentResult confirm(PaymentRequest request);

	PaymentResult queryByPaymentKey(String paymentKey);

	void cancel(String paymentKey, Long reservationId);
}
