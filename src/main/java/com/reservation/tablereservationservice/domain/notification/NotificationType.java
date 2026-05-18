package com.reservation.tablereservationservice.domain.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
	RESERVATION_CONFIRMED("예약 확정", "예약이 확정되었습니다"),
	RESERVATION_CANCELLED("예약 취소", "예약이 취소되었습니다"),
	RESERVATION_REMIND("방문 리마인드", "방문 예정일이 내일입니다");

	private final String description;
	private final String title;
}
