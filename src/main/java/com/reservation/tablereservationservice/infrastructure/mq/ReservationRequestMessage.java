package com.reservation.tablereservationservice.infrastructure.mq;

import java.time.LocalDate;

import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

public record ReservationRequestMessage(
	String userEmail,
	Long slotId,
	LocalDate date,
	int partySize,
	String note
) {

	public static ReservationRequestMessage from(String userEmail, ReservationRequestDto dto) {
		return new ReservationRequestMessage(
			userEmail,
			dto.getSlotId(),
			dto.getDate(),
			dto.getPartySize(),
			dto.getNote()
		);
	}

	public ReservationRequestDto toDto() {
		return new ReservationRequestDto(slotId, date, partySize, note);
	}
}