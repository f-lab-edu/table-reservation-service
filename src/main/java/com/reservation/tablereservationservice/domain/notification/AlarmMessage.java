package com.reservation.tablereservationservice.domain.notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AlarmMessage {
	private static final DateTimeFormatter CONTENT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final Long receiverId;
	private final Long reservationId;
	private final NotificationType type;
	private final String title;
	private final String content;

	public static AlarmMessage reservationConfirmed(Long receiverId, Long reservationId,
		LocalDateTime visitAt, int partySize) {
		return AlarmMessage.builder()
			.receiverId(receiverId)
			.reservationId(reservationId)
			.type(NotificationType.RESERVATION_CONFIRMED)
			.title(NotificationType.RESERVATION_CONFIRMED.getTitle())
			.content(visitAt.format(CONTENT_FORMATTER) + " / " + partySize + "명")
			.build();
	}

	public static AlarmMessage reservationCancelled(Long receiverId, Long reservationId,
		LocalDateTime visitAt, int partySize) {
		return AlarmMessage.builder()
			.receiverId(receiverId)
			.reservationId(reservationId)
			.type(NotificationType.RESERVATION_CANCELLED)
			.title(NotificationType.RESERVATION_CANCELLED.getTitle())
			.content(visitAt.format(CONTENT_FORMATTER) + " / " + partySize + "명")
			.build();
	}

	public static AlarmMessage ownerReservationConfirmed(Long ownerId, Long reservationId,
		LocalDateTime visitAt, int partySize) {
		return AlarmMessage.builder()
			.receiverId(ownerId)
			.reservationId(reservationId)
			.type(NotificationType.RESERVATION_CONFIRMED)
			.title("새 예약 접수")
			.content(visitAt.format(CONTENT_FORMATTER) + " / " + partySize + "명")
			.build();
	}

	public static AlarmMessage ownerReservationCancelled(Long ownerId, Long reservationId,
		LocalDateTime visitAt, int partySize) {
		return AlarmMessage.builder()
			.receiverId(ownerId)
			.reservationId(reservationId)
			.type(NotificationType.RESERVATION_CANCELLED)
			.title("예약 취소됨")
			.content(visitAt.format(CONTENT_FORMATTER) + " / " + partySize + "명")
			.build();
	}
}
