package com.reservation.tablereservationservice.infrastructure.payment.client;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
class TossErrorResponse {

	private String code;
	private String message;
}
