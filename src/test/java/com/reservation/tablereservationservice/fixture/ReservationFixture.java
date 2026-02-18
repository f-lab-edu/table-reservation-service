package com.reservation.tablereservationservice.fixture;

import java.time.LocalDateTime;

import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(fluent = true, chain = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReservationFixture {

	private Long reservationId = null;
	private Long userId = 1L;
	private Long slotId = 10L;

	private LocalDateTime visitAt = LocalDateTime.of(2030, 1, 1, 19, 0);
	private int partySize = 2;
	private String note = "note";
	private ReservationStatus status = ReservationStatus.CONFIRMED;

	public static ReservationFixture confirmed() {
		return new ReservationFixture().status(ReservationStatus.CONFIRMED);
	}

	public static ReservationFixture canceled() {
		return new ReservationFixture().status(ReservationStatus.CANCELED);
	}

	public Reservation build() {
		Reservation.ReservationBuilder builder = Reservation.builder()
			.userId(userId)
			.slotId(slotId)
			.visitAt(visitAt)
			.partySize(partySize)
			.note(note)
			.status(status);

		if (reservationId != null) {
			builder.reservationId(reservationId);
		}

		return builder.build();
	}
}
