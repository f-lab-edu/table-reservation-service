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
	private boolean isRead;
	private LocalDateTime createdAt;

	@Builder
	public Notification(Long notificationId, Long receiverId, Long reservationId, NotificationType type,
		String title, String content, boolean isRead, LocalDateTime createdAt) {
		this.notificationId = notificationId;
		this.receiverId = receiverId;
		this.reservationId = reservationId;
		this.type = type;
		this.title = title;
		this.content = content;
		this.isRead = isRead;
		this.createdAt = createdAt;
	}

	public void markAsRead() {
		this.isRead = true;
	}

	public static Notification from(AlarmMessage message) {
		return Notification.builder()
			.receiverId(message.getReceiverId())
			.reservationId(message.getReservationId())
			.type(message.getType())
			.title(message.getTitle())
			.content(message.getContent())
			.isRead(false)
			.build();
	}
}
