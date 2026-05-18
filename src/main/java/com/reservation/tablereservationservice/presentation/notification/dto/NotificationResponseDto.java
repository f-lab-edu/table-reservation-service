package com.reservation.tablereservationservice.presentation.notification.dto;

import java.time.LocalDateTime;

import com.reservation.tablereservationservice.domain.notification.Notification;
import com.reservation.tablereservationservice.domain.notification.NotificationType;

public record NotificationResponseDto(
	Long notificationId,
	Long reservationId,
	NotificationType type,
	String title,
	String content,
	boolean isRead,
	LocalDateTime createdAt
) {
	public static NotificationResponseDto from(Notification notification) {
		return new NotificationResponseDto(
			notification.getNotificationId(),
			notification.getReservationId(),
			notification.getType(),
			notification.getTitle(),
			notification.getContent(),
			notification.isRead(),
			notification.getCreatedAt()
		);
	}
}
