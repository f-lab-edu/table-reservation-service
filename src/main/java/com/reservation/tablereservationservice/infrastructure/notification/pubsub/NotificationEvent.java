package com.reservation.tablereservationservice.infrastructure.notification.pubsub;

import com.reservation.tablereservationservice.domain.notification.NotificationType;

public record NotificationEvent(
	Long receiverId,
	Long reservationId,
	NotificationType type,
	String title,
	String content
) {
}
