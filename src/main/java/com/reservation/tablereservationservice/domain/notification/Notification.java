package com.reservation.tablereservationservice.domain.notification;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
public class Notification {

	private Long notificationId;
	private Long receiverId;
	private Long reservationId;
	private NotificationType type;
	private String title;
	private String content;
	private LocalDateTime readAt;

	@Builder
	public Notification(Long notificationId, Long receiverId, Long reservationId, NotificationType type,
		String title, String content, LocalDateTime readAt) {
		this.notificationId = notificationId;
		this.receiverId = receiverId;
		this.reservationId = reservationId;
		this.type = type;
		this.title = title;
		this.content = content;
		this.readAt = readAt;
	}
}

