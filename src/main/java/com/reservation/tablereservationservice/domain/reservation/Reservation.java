package com.reservation.tablereservationservice.domain.reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
public class Reservation {

	private Long reservationId;
	private Long userId;
	private Long slotId;
	private LocalDate date;
	private LocalDateTime visitAt;
	private Integer partySize;
	private String note;
	private ReservationStatus status;

	@Builder
	public Reservation(Long reservationId, Long userId, Long slotId, LocalDate date,
		LocalDateTime visitAt, Integer partySize, String note, ReservationStatus status) {
		this.reservationId = reservationId;
		this.userId = userId;
		this.slotId = slotId;
		this.date = date;
		this.visitAt = visitAt;
		this.partySize = partySize;
		this.note = note;
		this.status = status;
	}

}
