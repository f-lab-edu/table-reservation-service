package com.reservation.tablereservationservice.application.payment;

import com.reservation.tablereservationservice.presentation.payment.dto.PaymentConfirmRequestDto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentRequest {

	private String paymentKey;
	private String orderId;
	private Integer amount;

	public static PaymentRequest from(PaymentConfirmRequestDto requestDto) {
		PaymentRequest request = new PaymentRequest();
		request.paymentKey = requestDto.getPaymentKey();
		request.orderId = requestDto.getOrderId();
		request.amount = requestDto.getAmount();
		return request;
	}
}
