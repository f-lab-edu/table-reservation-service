package com.reservation.tablereservationservice.domain.notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
	CONFIRMED("예약 확정", "예약이 확정되었습니다"),
	CANCELED("예약 취소", "예약이 취소되었습니다"),
	REMIND("방문 리마인드", "방문 예정일이 내일입니다");

	private static final DateTimeFormatter DATE_FORMATTER =
		DateTimeFormatter.ofPattern("M월 d일(E) HH:mm", Locale.KOREAN);

	private final String title;
	private final String description;

	public String customerContent(LocalDateTime visitAt, String restaurantName) {
		return restaurantName + " " + visitAt.format(DATE_FORMATTER) + " " + description + ".";
	}

	public String ownerContent(LocalDateTime visitAt, int partySize) {
		return visitAt.format(DATE_FORMATTER) + ", " + partySize + "명 " + description + ".";
	}
}
