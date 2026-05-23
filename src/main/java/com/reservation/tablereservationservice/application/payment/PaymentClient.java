package com.reservation.tablereservationservice.application.payment;

public interface PaymentClient {

	PaymentResult confirm(PaymentRequest request);
}
