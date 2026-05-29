package com.reservation.tablereservationservice.infrastructure.payment.messaging;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CancelQueueMessage {

	private Long reservationId;

	public CancelQueueMessage(Long reservationId) {
		this.reservationId = reservationId;
	}
}
