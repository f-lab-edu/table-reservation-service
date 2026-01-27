package com.reservation.tablereservationservice.presentation.reservation.dto;

import java.time.LocalDateTime;

import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
public class ReservationListResponseDto {

	private String userName;
	private String userPhone;
	private String note;
	private Long reservationId;
	private Long restaurantId;
	private String restaurantName;
	private LocalDateTime visitAt;
	private int partySize;
	private ReservationStatus status;

	@Builder
	public ReservationListResponseDto(
		String userName, String userPhone, String note, Long reservationId, Long restaurantId, String restaurantName,
		LocalDateTime visitAt,
		int partySize, ReservationStatus status
	) {
		this.userName = userName;
		this.userPhone = userPhone;
		this.note = note;
		this.reservationId = reservationId;
		this.restaurantId = restaurantId;
		this.restaurantName = restaurantName;
		this.visitAt = visitAt;
		this.partySize = partySize;
		this.status = status;
	}
}
