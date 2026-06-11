package com.reservation.tablereservationservice.infrastructure.payment.messaging;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CancelQueueMessage {

	public static final String OUTBOX_EVENT_TYPE = "RESERVATION_CANCEL";

	private Long reservationId;

	public static CancelQueueMessage of(Long reservationId) {
		CancelQueueMessage message = new CancelQueueMessage();
		message.reservationId = reservationId;
		return message;
	}
}
