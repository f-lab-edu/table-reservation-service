package com.reservation.tablereservationservice.domain.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
	RESERVATION_CONFIRMED("예약 확정"),
	RESERVATION_CANCELLED("예약 취소"),
	RESERVATION_REMIND("방문 리마인드");

	private final String description;
}
