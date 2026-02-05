package com.reservation.tablereservationservice.domain.notification;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AlarmMessage {
	private final Long receiverId;
	private final Long reservationId;
	private final NotificationType type;
	private final String title;
	private final String content;

}
