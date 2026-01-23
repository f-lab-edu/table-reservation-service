package com.reservation.tablereservationservice.presentation.reservation.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReservationRequestDto {

	private Long slotId;
	private LocalDate date;
	private int partySize;
	private String note;
}
