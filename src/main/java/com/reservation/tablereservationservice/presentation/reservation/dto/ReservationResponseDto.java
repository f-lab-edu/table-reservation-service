package com.reservation.tablereservationservice.presentation.reservation.dto;

import java.time.LocalDateTime;

import com.reservation.tablereservationservice.application.reservation.service.ReservationCreateResult;
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
	private String idempotencyKey;
	private Integer depositAmount;

	@Builder
	public ReservationResponseDto(Long reservationId, Integer partySize, ReservationStatus status,
		LocalDateTime visitAt, String idempotencyKey, Integer depositAmount) {
		this.reservationId = reservationId;
		this.partySize = partySize;
		this.status = status;
		this.visitAt = visitAt;
		this.idempotencyKey = idempotencyKey;
		this.depositAmount = depositAmount;
	}

	public static ReservationResponseDto from(Reservation reservation) {
		return ReservationResponseDto.builder()
			.reservationId(reservation.getReservationId())
			.partySize(reservation.getPartySize())
			.status(reservation.getStatus())
			.visitAt(reservation.getVisitAt())
			.idempotencyKey(reservation.getIdempotencyKey())
			.build();
	}

	public static ReservationResponseDto from(ReservationCreateResult result) {
		return ReservationResponseDto.builder()
			.reservationId(result.reservation().getReservationId())
			.partySize(result.reservation().getPartySize())
			.status(result.reservation().getStatus())
			.visitAt(result.reservation().getVisitAt())
			.idempotencyKey(result.reservation().getIdempotencyKey())
			.depositAmount(result.depositAmount())
			.build();
	}
}