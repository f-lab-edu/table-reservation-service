package com.reservation.tablereservationservice.domain.notification;

import java.time.LocalDateTime;

import com.reservation.tablereservationservice.domain.reservation.Reservation;

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

	public static Notification customerConfirmed(Reservation reservation, String restaurantName) {
		return create(reservation.getUserId(), reservation, NotificationType.CONFIRMED,
				NotificationType.CONFIRMED.customerContent(reservation.getVisitAt(), restaurantName));
	}

	public static Notification ownerConfirmed(Reservation reservation, Long ownerId) {
		return create(ownerId, reservation, NotificationType.CONFIRMED,
				NotificationType.CONFIRMED.ownerContent(reservation.getVisitAt(), reservation.getPartySize()));
	}

	public static Notification customerCanceled(Reservation reservation, String restaurantName) {
		return create(reservation.getUserId(), reservation, NotificationType.CANCELED,
				NotificationType.CANCELED.customerContent(reservation.getVisitAt(), restaurantName));
	}

	public static Notification ownerCanceled(Reservation reservation, Long ownerId) {
		return create(ownerId, reservation, NotificationType.CANCELED,
				NotificationType.CANCELED.ownerContent(reservation.getVisitAt(), reservation.getPartySize()));
	}

	private static Notification create(Long receiverId, Reservation reservation,
			NotificationType type, String content) {
		return Notification.builder()
				.receiverId(receiverId)
				.reservationId(reservation.getReservationId())
				.type(type)
				.title(type.getTitle())
				.content(content)
				.isRead(false)
				.build();
	}
}
