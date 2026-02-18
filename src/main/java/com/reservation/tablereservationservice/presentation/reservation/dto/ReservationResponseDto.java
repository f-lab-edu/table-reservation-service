package com.reservation.tablereservationservice.presentation.reservation.dto;

import java.time.LocalDateTime;

import com.reservation.tablereservationservice.domain.reservation.Reservation;
import com.reservation.tablereservationservice.domain.reservation.ReservationStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
public class ReservationResponseDto {

	private Long reservationId;
	private Integer partySize;
	private ReservationStatus status;
	private LocalDateTime visitAt;
	private String requestId;
	private long serverReceivedSeq;

	@Builder
	public ReservationResponseDto(Long reservationId, Integer partySize, ReservationStatus status, LocalDateTime visitAt,
		String requestId, long serverReceivedSeq) {
		this.reservationId = reservationId;
		this.partySize = partySize;
		this.status = status;
		this.visitAt = visitAt;
		this.requestId = requestId;
		this.serverReceivedSeq = serverReceivedSeq;
	}

	public static ReservationResponseDto from(Reservation reservation) {
		return ReservationResponseDto.builder()
			.reservationId(reservation.getReservationId())
			.partySize(reservation.getPartySize())
			.status(reservation.getStatus())
			.visitAt(reservation.getVisitAt())
			.requestId(reservation.getRequestId())
			.serverReceivedSeq(reservation.getServerReceivedSeq())
			.build();
	}
}
