package com.reservation.tablereservationservice.infrastructure.stream;

import java.time.LocalDate;
import java.util.Map;

import com.reservation.tablereservationservice.presentation.reservation.dto.ReservationRequestDto;

public record ReservationRequestMessage(
	String userEmail,
	Long slotId,
	LocalDate date,
	int partySize,
	String note
) {

	public static ReservationRequestMessage fromMap(Map<String, String> body) {
		return new ReservationRequestMessage(
			body.get("userEmail"),
			Long.parseLong(body.get("slotId")),
			LocalDate.parse(body.get("date")),
			Integer.parseInt(body.get("partySize")),
			body.get("note")
		);
	}

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
